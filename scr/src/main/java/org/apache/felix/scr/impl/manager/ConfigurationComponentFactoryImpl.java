/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.scr.impl.manager;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.felix.scr.impl.TargetedPID;
import org.apache.felix.scr.impl.config.ComponentContainer;
import org.apache.felix.scr.impl.config.ComponentManager;
import org.apache.felix.scr.impl.helper.ComponentMethods;
import org.osgi.framework.Constants;
import org.osgi.service.log.LogService;

/**
 * This class implements the Felix non-standard and obsolete extended compponent factory
 * that allows factory instances to be created through config admin using the component factory pid
 * as a factory pid.  Do not use this for new code, use a plain component instead.
 * Use of this behavior can be turned on globally with the framework config or config admin
 * property <code>ds.factory.enabled</code> bundle context property
 * set to <code>true</code> or turned on per-component with a component descriptor attribute
 * xmlns:felix="http://felix.apache.org/xmlns/scr/extensions/v1.0.0"
 * felix:obsoleteFactoryComponentFactory='true'
 */
public class ConfigurationComponentFactoryImpl<S> extends ComponentFactoryImpl<S>
{

    /**
     * The map of components created from Configuration objects maps PID to
     * {@link org.apache.felix.scr.impl.manager.SingleComponentManager} for configuration updating this map is
     * lazily created.
     */
    private final Map<String, SingleComponentManager<S>> m_configuredServices = new HashMap<String, SingleComponentManager<S>>();

    public ConfigurationComponentFactoryImpl( ComponentContainer container )
    {
        super( container );
    }


    @Override
    public Dictionary<String, Object> getServiceProperties()
    {
        Dictionary<String, Object> props = super.getServiceProperties();
        // also register with the factory PID
        props.put( Constants.SERVICE_PID, getComponentMetadata().getConfigurationPid() );

        // descriptive service properties
        props.put( Constants.SERVICE_DESCRIPTION, "Configurable (nonstandard) Factory Component "
            + getComponentMetadata().getName() );
        
        return props;
    }


    /**
     * The component factory does not have a component to create.
     * <p>
     * But in the backwards compatible case any instances created for factory
     * configuration instances are to enabled as a consequence of activating
     * the component factory.
     */
    boolean getServiceInternal()
    {
        List<AbstractComponentManager<S>> cms = new ArrayList<AbstractComponentManager<S>>( );
        getComponentManagers( m_configuredServices, cms );
        for ( Iterator i = cms.iterator(); i.hasNext(); )
        {
            ((AbstractComponentManager)i.next()).enable( false );
        }

        m_activated = true;
        return true;
    }


    /**
     * The component factory does not have a component to delete.
     * <p>
     * But in the backwards compatible case any instances created for factory
     * configuration instances are to disabled as a consequence of deactivating
     * the component factory.
     */
    @Override
    protected void deleteComponent( int reason )
    {
        List<AbstractComponentManager<S>> cms = new ArrayList<AbstractComponentManager<S>>( );
        getComponentManagers( m_configuredServices, cms );
        for ( AbstractComponentManager<S> cm: cms )
        {
            cm.disable();
        }
    }


    //---------- ComponentHolder interface

//    public void configurationDeleted( TargetedPID pid, TargetedPID factoryPid )
//    {
//        if ( pid.equals( getComponentMetadata().getConfigurationPid() ) )
//        {
//            super.configurationDeleted( pid, factoryPid );
//        }
//        else
//        {
//            SingleComponentManager<S> cm;
//            synchronized ( m_configuredServices )
//            {
//                cm = m_configuredServices.remove( pid );
//            }
//
//            if ( cm != null )
//            {
//                log( LogService.LOG_DEBUG, "Disposing component after configuration deletion", null );
//
//                cm.dispose();
//            }
//        }
//    }
//
//
//    public boolean configurationUpdated( TargetedPID targetedPid, TargetedPID factoryTargetedPid, Dictionary<String, Object> configuration, long changeCount )
//    {
//    	return false;
////        if ( pid.equals( getComponentMetadata().getConfigurationPid() ) )
////        {
////            return super.configurationUpdated( targetedPid, factoryTargetedPid, configuration, changeCount );
////        }
////        else   //non-spec backwards compatible
////        {
////            SingleComponentManager<S> cm;
////            synchronized ( m_configuredServices )
////            {
////                cm = m_configuredServices.get( pid );
////            }
////
////            if ( cm == null )
////            {
////                // create a new instance with the current configuration
////                cm = createConfigurationComponentManager();
////
////                // this should not call component reactivation because it is
////                // not active yet
////                cm.reconfigure( configuration, changeCount, m_targetedPID );
////
////                // enable asynchronously if components are already enabled
////                if ( getState() == STATE_FACTORY )
////                {
////                    cm.enable( false );
////                }
////
////                synchronized ( m_configuredServices )
////                {
////                    // keep a reference for future updates
////                    m_configuredServices.put( pid, cm );
////                }
////                return true;
////
////            }
////            else
////            {
////                // update the configuration as if called as ManagedService
////                cm.reconfigure( configuration, changeCount, m_targetedPID );
////                return false;
////            }
////        }
//    }
//

    public List<? extends ComponentManager<S>> getComponents()
    {
        List<AbstractComponentManager<S>> cms = (List<AbstractComponentManager<S>>) super.getComponents();
        getComponentManagers( m_configuredServices, cms );
        return cms;
    }


    /**
     * Disposes off all components ever created by this component holder. This
     * method is called if either the Declarative Services runtime is stopping
     * or if the owning bundle is stopped. In both cases all components created
     * by this holder must be disposed off.
     */
    public void disposeComponents( int reason )
    {
        super.disposeComponents( reason );

        List<AbstractComponentManager<S>> cms = new ArrayList<AbstractComponentManager<S>>( );
        getComponentManagers( m_configuredServices, cms );
        for ( AbstractComponentManager acm: cms )
        {
            acm.dispose( reason );
        }

        m_configuredServices.clear();

        // finally dispose the component factory itself
        dispose( reason );
    }

    public synchronized long getChangeCount( String pid)
    {

        if (pid.equals( getComponentMetadata().getConfigurationPid())) 
        {
            return m_changeCount;
        }
        synchronized ( m_configuredServices )
        {
            SingleComponentManager icm = m_configuredServices.get( pid );
            return icm == null? -1: -2; //TODO fix this icm.getChangeCount();
        }
    }


    //---------- internal


    /**
     * Creates an {@link org.apache.felix.scr.impl.manager.SingleComponentManager} instance with the
     * {@link org.apache.felix.scr.impl.BundleComponentActivator} and {@link org.apache.felix.scr.impl.metadata.ComponentMetadata} of this
     * instance. The component manager is kept in the internal set of created
     * components. The component is neither configured nor enabled.
     */
    private SingleComponentManager<S> createConfigurationComponentManager()
    {
        return new ComponentFactoryConfiguredInstance<S>( this, getComponentMethods() );
    }

    static class ComponentFactoryConfiguredInstance<S> extends SingleComponentManager<S> {

        public ComponentFactoryConfiguredInstance( ComponentContainer container, ComponentMethods componentMethods )
        {
            super( container, componentMethods, true );
        }

        public boolean isImmediate()
        {
            return true;
        }
    }
}
