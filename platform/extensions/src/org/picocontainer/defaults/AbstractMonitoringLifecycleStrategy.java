/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *****************************************************************************/
package org.picocontainer.defaults;

import org.picocontainer.ComponentMonitor;

import java.io.Serializable;

/**
 * Abstract base class for lifecycle strategy implementation supporting a {@link ComponentMonitor}.
 *
 * @author J&ouml;rg Schaible
 * @since 1.2
 */
public abstract class AbstractMonitoringLifecycleStrategy implements LifecycleStrategy, ComponentMonitorStrategy, Serializable {

    private ComponentMonitor componentMonitor;

    /**
     * Construct a AbstractMonitoringLifecylceStrategy.
     *
     * @param monitor the componentMonitor to use
     * @throws NullPointerException if the monitor is <code>null</code>
     */
    public AbstractMonitoringLifecycleStrategy(ComponentMonitor monitor) {
        if (monitor == null) {
            throw new NullPointerException("Monitor is null");
        }
        this.componentMonitor = monitor;
    }

    @Override
    public void changeMonitor(ComponentMonitor monitor) {
        if (monitor == null) {
            throw new NullPointerException("Monitor is null");
        }
        this.componentMonitor = monitor;
    }

    @Override
    public ComponentMonitor currentMonitor() {
        return componentMonitor;
    }

}
