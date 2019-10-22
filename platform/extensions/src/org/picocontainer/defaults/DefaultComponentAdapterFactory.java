/*****************************************************************************
 * Copyright (c) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Idea by Rachel Davies, Original code by Aslak Hellesoy and Paul Hammant   *
 *****************************************************************************/

package org.picocontainer.defaults;

import org.picocontainer.ComponentAdapter;
import org.picocontainer.ComponentMonitor;
import org.picocontainer.Parameter;
import org.picocontainer.PicoIntrospectionException;
import org.picocontainer.monitors.DefaultComponentMonitor;

/**
 * Creates instances of {@link ConstructorInjectionComponentAdapter} decorated by
 * {@link CachingComponentAdapter}.
 *
 * @author Jon Tirs&eacute;n
 * @author Aslak Helles&oslash;y
 * @version $Revision: 2779 $
 */
public class DefaultComponentAdapterFactory extends MonitoringComponentAdapterFactory {

    private final LifecycleStrategy lifecycleStrategy;

    public DefaultComponentAdapterFactory(ComponentMonitor monitor) {
        super(monitor);
        this.lifecycleStrategy = new DefaultLifecycleStrategy(monitor);
    }

    public DefaultComponentAdapterFactory(ComponentMonitor monitor, LifecycleStrategy lifecycleStrategy) {
        super(monitor);
        this.lifecycleStrategy = lifecycleStrategy;
    }

    public DefaultComponentAdapterFactory() {
        this.lifecycleStrategy = new DefaultLifecycleStrategy(new DefaultComponentMonitor());
    }

    @Override
    public ComponentAdapter createComponentAdapter(Object componentKey, Class componentImplementation, Parameter[] parameters) throws PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {
        return new CachingComponentAdapter(new ConstructorInjectionComponentAdapter(componentKey, componentImplementation, parameters, false, currentMonitor(), lifecycleStrategy));
    }

    @Override
    public void changeMonitor(ComponentMonitor monitor) {
        super.changeMonitor(monitor);
        if (lifecycleStrategy instanceof ComponentMonitorStrategy) {
            ((ComponentMonitorStrategy) lifecycleStrategy).changeMonitor(monitor);
        }
    }

}
