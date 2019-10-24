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

/**
 * Component adapter which decorates another adapter.
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
public class DecoratingComponentAdapter implements ComponentAdapter, LifecycleManager, LifecycleStrategy {
    private final ComponentAdapter delegate;

    public DecoratingComponentAdapter(ComponentAdapter delegate) {
         this.delegate = delegate;
    }

    @Override
    public Object getComponentKey() {
        return delegate.getComponentKey();
    }

    @Override
    public Class<?> getComponentImplementation() {
        return delegate.getComponentImplementation();
    }

    @Override
    public Object getComponentInstance(PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
        return delegate.getComponentInstance(container);
    }

    public ComponentAdapter getDelegate() {
        return delegate;
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
        return "[" +
               getPrintableClassName() +
               " delegate=" +
               delegate +
               "]";
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

