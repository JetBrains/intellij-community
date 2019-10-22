/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by Mauro Talevi                                             *
 *****************************************************************************/
package org.picocontainer.defaults;

import org.picocontainer.ComponentMonitor;

import java.io.Serializable;

/**
 * Abstract {@link ComponentAdapterFactory ComponentAdapterFactory} supporting a
 * {@link ComponentMonitorStrategy ComponentMonitorStrategy}.
 * It provides a {@link DelegatingComponentMonitor default ComponentMonitor},
 * but does not allow to use <code>null</code> for the component monitor.
 *
 * @author Mauro Talevi
 * @see ComponentAdapterFactory
 * @see ComponentMonitorStrategy
 * @since 1.2
 */
public abstract class MonitoringComponentAdapterFactory implements ComponentAdapterFactory, ComponentMonitorStrategy, Serializable {
    private ComponentMonitor componentMonitor;

    /**
     * Constructs a MonitoringComponentAdapterFactory with a custom monitor
     * @param monitor the ComponentMonitor used by the factory
     */
    protected MonitoringComponentAdapterFactory(ComponentMonitor monitor) {
        if (monitor == null){
            throw new NullPointerException("componentMonitor");
        }
        this.componentMonitor = monitor;
    }

    /**
     * Constructs a  MonitoringComponentAdapterFactory with a {@link DelegatingComponentMonitor default monitor}.
     */
    protected MonitoringComponentAdapterFactory() {
        this(new DelegatingComponentMonitor());
    }

    @Override
    public void changeMonitor(ComponentMonitor monitor) {
        this.componentMonitor = monitor;
    }

    /**
     * Returns the monitor currently used
     * @return The ComponentMonitor currently used
     */
    @Override
    public ComponentMonitor currentMonitor(){
        return componentMonitor;
    }

}
