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
import org.picocontainer.monitors.DefaultComponentMonitor;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * <p>
 * A {@link ComponentMonitor monitor} which delegates to another monitor.
 * It provides a {@link DefaultComponentMonitor default ComponentMonitor},
 * but does not allow to use <code>null</code> for the delegate.
 * </p>
 * <p>
 * It also supports a {@link ComponentMonitorStrategy monitor strategy}
 * that allows to change the delegate.
 * </p>
 *
 * @author Mauro Talevi
 * @version $Revision: $
 * @since 1.2
 */
public class DelegatingComponentMonitor implements ComponentMonitor, ComponentMonitorStrategy, Serializable {

    private  ComponentMonitor delegate;

    /**
     * Creates a DelegatingComponentMonitor with a given delegate
     * @param delegate the ComponentMonitor to which this monitor delegates
     */
    public DelegatingComponentMonitor(ComponentMonitor delegate) {
        checkMonitor(delegate);
        this.delegate = delegate;
    }

    /**
     * Creates a DelegatingComponentMonitor with an instance of
     * {@link DefaultComponentMonitor}.
     */
    public DelegatingComponentMonitor() {
        this(DefaultComponentMonitor.getInstance());
    }

    @Override
    public void instantiating(Constructor constructor) {
        delegate.instantiating(constructor);
    }

    @Override
    public void instantiated(Constructor constructor, long duration) {
        delegate.instantiated(constructor, duration);
    }

    @Override
    public void instantiationFailed(Constructor constructor, Exception e) {
        delegate.instantiationFailed(constructor, e);
    }

    @Override
    public void invoking(Method method, Object instance) {
        delegate.invoking(method, instance);
    }

    @Override
    public void invoked(Method method, Object instance, long duration) {
        delegate.invoked(method, instance, duration);
    }

    @Override
    public void invocationFailed(Method method, Object instance, Exception e) {
        delegate.invocationFailed(method, instance, e);
    }

    @Override
    public void lifecycleInvocationFailed(Method method, Object instance, RuntimeException cause) {
        delegate.lifecycleInvocationFailed(method,instance, cause);
    }

    /**
     * If the delegate supports a {@link ComponentMonitorStrategy monitor strategy},
     * this is used to changed the monitor while keeping the same delegate.
     * Else the delegate is replaced by the new monitor.
     * {@inheritDoc}
     */
    @Override
    public void changeMonitor(ComponentMonitor monitor) {
        checkMonitor(monitor);
        if ( delegate instanceof ComponentMonitorStrategy ){
            ((ComponentMonitorStrategy)delegate).changeMonitor(monitor);
        } else {
            delegate = monitor;
        }
    }

    @Override
    public ComponentMonitor currentMonitor() {
        if ( delegate instanceof ComponentMonitorStrategy ){
            return ((ComponentMonitorStrategy)delegate).currentMonitor();
        } else {
            return delegate;
        }
    }

    private void checkMonitor(ComponentMonitor monitor) {
        if ( monitor == null ){
            throw new NullPointerException("monitor");
        }
    }

}
