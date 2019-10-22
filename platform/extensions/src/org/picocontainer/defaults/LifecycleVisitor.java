/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *****************************************************************************/
package org.picocontainer.defaults;

import org.picocontainer.ComponentMonitor;
import org.picocontainer.Disposable;
import org.picocontainer.LifecycleManager;
import org.picocontainer.PicoIntrospectionException;
import org.picocontainer.monitors.DefaultComponentMonitor;

import java.lang.reflect.Method;


/**
 * A PicoVisitor for the lifecycle methods of the components.
 *
 * @author Aslak Helles&oslash;y
 * @author J&ouml;rg Schaible
 * @since 1.1
 * @deprecated since 1.2 in favour of {@link LifecycleManager}
 */
public class LifecycleVisitor extends MethodCallingVisitor {
    private static final Method DISPOSE;
    static {
        try {
            DISPOSE = Disposable.class.getMethod("dispose", (Class[])null);
        } catch (NoSuchMethodException e) {
            // /CLOVER:OFF
            throw new InternalError(e.getMessage());
            // /CLOVER:ON
        }
    }

    private final ComponentMonitor componentMonitor;

    /**
     * Construct a LifecycleVisitor.
     *
     * @param method the method to call
     * @param ofType the component type
     * @param visitInInstantiationOrder flag for the visiting order
     * @param monitor the {@link ComponentMonitor} to use
     * @deprecated since 1.2 in favour of {@link LifecycleManager}
     */
    protected LifecycleVisitor(Method method, Class ofType, boolean visitInInstantiationOrder, ComponentMonitor monitor) {
        super(method, ofType, null, visitInInstantiationOrder);
        if (monitor == null) {
            throw new NullPointerException();
        }
        this.componentMonitor = monitor;
    }

    /**
     * Construct a LifecycleVisitor.
     *
     * @param method the method to call
     * @param ofType the component type
     * @param visitInInstantiationOrder flag for the visiting order
     * @deprecated since 1.2 in favour of {@link LifecycleManager}
     */
    public LifecycleVisitor(Method method, Class ofType, boolean visitInInstantiationOrder) {
        this(method, ofType, visitInInstantiationOrder, new DefaultComponentMonitor());
    }

    /**
     * Invoke the standard PicoContainer lifecycle for {@link Disposable#dispose()}.
     *
     * @param node The node to start the traversal.
     * @deprecated since 1.2 in favour of {@link LifecycleManager}
     */
    public static void dispose(Object node) {
        new LifecycleVisitor(DISPOSE, Disposable.class, false).traverse(node);
    }

    @Override
    protected Object invoke(final Object target) {
        final Method method = getMethod();
        try {
            componentMonitor.invoking(method, target);
            final long startTime = System.currentTimeMillis();
            super.invoke(target);
            componentMonitor.invoked(method, target, System.currentTimeMillis() - startTime);
        } catch (final PicoIntrospectionException e) {
            componentMonitor.invocationFailed(method, target, (Exception)e.getCause());
            throw e;
        }
        return Void.TYPE;
    }

}
