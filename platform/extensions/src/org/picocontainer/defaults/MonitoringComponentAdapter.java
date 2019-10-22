/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
 *****************************************************************************/
package org.picocontainer.defaults;

import org.picocontainer.ComponentAdapter;
import org.picocontainer.ComponentMonitor;

import java.io.Serializable;

/**
 * Abstract {@link ComponentAdapter ComponentAdapter} supporting a
 * {@link ComponentMonitorStrategy ComponentMonitorStrategy}.
 * It provides a {@link DelegatingComponentMonitor default ComponentMonitor},
 * but does not allow to use <code>null</code> for the component monitor.
 *
 * @author Mauro Talevi
 * @version $Revision: $
 * @see ComponentAdapter
 * @see ComponentMonitorStrategy
 * @since 1.2
 */
public abstract class MonitoringComponentAdapter implements ComponentAdapter, ComponentMonitorStrategy, Serializable {
    private ComponentMonitor componentMonitor;

    /**
     * Constructs a MonitoringComponentAdapter with a custom monitor
     * @param monitor the component monitor used by this ComponentAdapter
     */
    protected MonitoringComponentAdapter(ComponentMonitor monitor) {
        if (monitor == null){
            throw new NullPointerException("monitor");
        }
        this.componentMonitor = monitor;
    }

    /**
     * Constructs a MonitoringComponentAdapter with a {@link DelegatingComponentMonitor default monitor}.
     */
    protected MonitoringComponentAdapter() {
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
