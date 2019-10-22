/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by Jon Tirsen                                               *
 *****************************************************************************/

package org.picocontainer.defaults;

import org.picocontainer.*;

import java.io.Serializable;

/**
 * <p>
 * Component adapter which decorates another adapter.
 * </p>
 * <p>
 * This adapter supports a {@link ComponentMonitorStrategy component monitor strategy}
 * and will propagate change of monitor to the delegate if the delegate itself
 * support the monitor strategy.
 * </p>
 * <p>
 * This adapter also supports a {@link LifecycleManager lifecycle manager} and a
 * {@link LifecycleStrategy lifecycle strategy} if the delegate does.
 * </p>
 *
 * @author Jon Tirsen
 * @author Aslak Hellesoy
 * @author Mauro Talevi
 * @version $Revision: 2631 $
 */
public class DecoratingComponentAdapter implements ComponentAdapter, ComponentMonitorStrategy,
                                                    LifecycleManager, LifecycleStrategy, Serializable {

    private ComponentAdapter delegate;

    public DecoratingComponentAdapter(ComponentAdapter delegate) {
         this.delegate = delegate;
    }

    @Override
    public Object getComponentKey() {
        return delegate.getComponentKey();
    }

    @Override
    public Class getComponentImplementation() {
        return delegate.getComponentImplementation();
    }

    @Override
    public Object getComponentInstance(PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
        return delegate.getComponentInstance(container);
    }

    @Override
    public void verify(PicoContainer container) throws PicoIntrospectionException {
        delegate.verify(container);
    }

    public ComponentAdapter getDelegate() {
        return delegate;
    }

    @Override
    public void accept(PicoVisitor visitor) {
        visitor.visitComponentAdapter(this);
        delegate.accept(visitor);
    }

    /**
     * Delegates change of monitor if the delegate supports
     * a component monitor strategy.
     * {@inheritDoc}
     */
    @Override
    public void changeMonitor(ComponentMonitor monitor) {
        if ( delegate instanceof ComponentMonitorStrategy ){
            ((ComponentMonitorStrategy)delegate).changeMonitor(monitor);
        }
    }

    /**
     * Returns delegate's current monitor if the delegate supports
     * a component monitor strategy.
     * {@inheritDoc}
     * @throws PicoIntrospectionException if no component monitor is found in delegate
     */
    @Override
    public ComponentMonitor currentMonitor() {
        if ( delegate instanceof ComponentMonitorStrategy ){
            return ((ComponentMonitorStrategy)delegate).currentMonitor();
        }
        throw new PicoIntrospectionException("No component monitor found in delegate");
    }

    // ~~~~~~~~ LifecylceManager ~~~~~~~~

    /**
     * Invokes delegate start method if the delegate is a LifecycleManager
     * {@inheritDoc}
     */
    @Override
    public void start(PicoContainer container) {
        if ( delegate instanceof LifecycleManager ){
            ((LifecycleManager)delegate).start(container);
        }
    }

    /**
     * Invokes delegate stop method if the delegate is a LifecycleManager
     * {@inheritDoc}
     */
    @Override
    public void stop(PicoContainer container) {
        if ( delegate instanceof LifecycleManager ){
            ((LifecycleManager)delegate).stop(container);
        }
    }

    /**
     * Invokes delegate dispose method if the delegate is a LifecycleManager
     * {@inheritDoc}
     */
    @Override
    public void dispose(PicoContainer container) {
        if ( delegate instanceof LifecycleManager ){
            ((LifecycleManager)delegate).dispose(container);
        }
    }

    /**
     * Invokes delegate hasLifecylce method if the delegate is a LifecycleManager
     * {@inheritDoc}
     */
    @Override
    public boolean hasLifecycle() {
        if ( delegate instanceof LifecycleManager ){
            return ((LifecycleManager)delegate).hasLifecycle();
        }
        if ( delegate instanceof LifecycleStrategy ){
            return ((LifecycleStrategy)delegate).hasLifecycle(delegate.getComponentImplementation());
        }
        return false;
    }

    // ~~~~~~~~ LifecylceStrategy ~~~~~~~~

    /**
     * Invokes delegate start method if the delegate is a LifecycleStrategy
     * {@inheritDoc}
     */
    @Override
    public void start(Object component) {
        if ( delegate instanceof LifecycleStrategy ){
            ((LifecycleStrategy)delegate).start(component);
        }
    }

    /**
     * Invokes delegate stop method if the delegate is a LifecycleStrategy
     * {@inheritDoc}
     */
    @Override
    public void stop(Object component) {
        if ( delegate instanceof LifecycleStrategy ){
            ((LifecycleStrategy)delegate).stop(component);
        }
    }

    /**
     * Invokes delegate dispose method if the delegate is a LifecycleStrategy
     * {@inheritDoc}
     */
    @Override
    public void dispose(Object component) {
        if ( delegate instanceof LifecycleStrategy ){
            ((LifecycleStrategy)delegate).dispose(component);
        }
    }

    /**
     * Invokes delegate hasLifecylce(Class) method if the delegate is a LifecycleStrategy
     * {@inheritDoc}
     */
    @Override
    public boolean hasLifecycle(Class type) {
        if ( delegate instanceof LifecycleStrategy ){
            return ((LifecycleStrategy)delegate).hasLifecycle(type);
        }
        return false;
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[");
        buffer.append(getPrintableClassName());
        buffer.append(" delegate=");
        buffer.append(delegate);
        buffer.append("]");
        return buffer.toString();
    }

    private String getPrintableClassName() {
        String name = getClass().getName();
        name = name.substring(name.lastIndexOf('.')+1);
        if (name.endsWith("ComponentAdapter")) {
            name = name.substring(0, name.length() - "ComponentAdapter".length()) + "CA";
        }
        return name;
    }

}

