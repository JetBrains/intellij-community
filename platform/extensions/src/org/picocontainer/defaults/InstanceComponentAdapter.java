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

import org.picocontainer.LifecycleManager;
import org.picocontainer.PicoContainer;

/**
 * <p>
 * Component adapter which wraps a component instance.
 * </p>
 * <p>
 * This component adapter supports both a {@link LifecycleManager LifecycleManager} and a
 * {@link LifecycleStrategy LifecycleStrategy} to control the lifecycle of the component.
 * The lifecycle manager methods simply delegate to the lifecycle strategy methods
 * on the component instance.
 * </p>
 *
 * @author Aslak Helles&oslash;y
 * @author Paul Hammant
 * @author Mauro Talevi
 * @version $Revision: 2823 $
 */
public class InstanceComponentAdapter extends AbstractComponentAdapter implements LifecycleManager, LifecycleStrategy {
    private Object componentInstance;
    private LifecycleStrategy lifecycleStrategy;

    public InstanceComponentAdapter(Object componentKey, Object componentInstance) throws AssignabilityRegistrationException, NotConcreteRegistrationException {
        this(componentKey, componentInstance, new DefaultLifecycleStrategy());
    }

    public InstanceComponentAdapter(Object componentKey, Object componentInstance, LifecycleStrategy lifecycleStrategy) throws AssignabilityRegistrationException, NotConcreteRegistrationException {
        super(componentKey, getInstanceClass(componentInstance));
        this.componentInstance = componentInstance;
        this.lifecycleStrategy = lifecycleStrategy;
    }

    private static Class getInstanceClass(Object componentInstance) {
        if (componentInstance == null) {
            throw new NullPointerException("componentInstance cannot be null");
        }
        return componentInstance.getClass();
    }

    @Override
    public Object getComponentInstance(PicoContainer container) {
        return componentInstance;
    }

    @Override
    public void verify(PicoContainer container) {
    }

    // ~~~~~~~~ LifecylceManager ~~~~~~~~

    @Override
    public void start(PicoContainer container) {
        start(componentInstance);
    }

    @Override
    public void stop(PicoContainer container) {
        stop(componentInstance);
    }

    @Override
    public void dispose(PicoContainer container) {
        dispose(componentInstance);
    }

    @Override
    public boolean hasLifecycle() {
        return hasLifecycle(componentInstance.getClass());
    }

    // ~~~~~~~~ LifecylceStrategy ~~~~~~~~

    @Override
    public void start(Object component) {
        lifecycleStrategy.start(componentInstance);
    }

    @Override
    public void stop(Object component) {
        lifecycleStrategy.stop(componentInstance);
    }

    @Override
    public void dispose(Object component) {
        lifecycleStrategy.dispose(componentInstance);
    }

    @Override
    public boolean hasLifecycle(Class type) {
        return lifecycleStrategy.hasLifecycle(type);
    }

}
