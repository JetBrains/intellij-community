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

import org.picocontainer.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * This component adapter makes it possible to hide the implementation
 * of a real subject (behind a proxy) provided the key is an interface.
 * <p/>
 * This class exists here, because a) it has no deps on external jars, b) dynamic proxy is quite easy.
 * The user is prompted to look at picocontainer-gems for alternate and bigger implementations.
 *
 * @author Aslak Helles&oslash;y
 * @author Paul Hammant
 * @see org.picocontainer.gems.HotSwappingComponentAdapter for a more feature-rich version of this class.
 * @since 1.2, moved from package {@link org.picocontainer.alternatives}
 */
public class ImplementationHidingComponentAdapter extends DecoratingComponentAdapter {
    private final boolean strict;

    /**
     * Creates an ImplementationHidingComponentAdapter with a delegate
     * @param delegate the component adapter to which this adapter delegates
     * @param strict the scrict mode boolean
     */
    public ImplementationHidingComponentAdapter(ComponentAdapter delegate, boolean strict) {
        super(delegate);
        this.strict = strict;
    }

    @Override
    public Object getComponentInstance(final PicoContainer container)
            throws PicoInitializationException, PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {

        Object componentKey = getDelegate().getComponentKey();
        Class[] classes = null;
        if (componentKey instanceof Class && ((Class) getDelegate().getComponentKey()).isInterface()) {
            classes = new Class[]{(Class) getDelegate().getComponentKey()};
        } else if (componentKey instanceof Class[]) {
            classes = (Class[]) componentKey;
        } else {
            if(strict) {
                throw new PicoIntrospectionException("In strict mode, " + getClass().getName() + " only allows components registered with interface keys (java.lang.Class or java.lang.Class[])");
            }
            return getDelegate().getComponentInstance(container);
        }

        Class[] interfaces = verifyInterfacesOnly(classes);
        return createProxy(interfaces, container, getDelegate().getComponentImplementation().getClassLoader());
    }

    private Object createProxy(Class[] interfaces, final PicoContainer container, final ClassLoader classLoader) {
        return Proxy.newProxyInstance(classLoader,
                interfaces, new InvocationHandler() {
                    @Override
                    public Object invoke(final Object proxy, final Method method,
                                         final Object[] args)
                            throws Throwable {
                        Object componentInstance = getDelegate().getComponentInstance(container);
                        ComponentMonitor componentMonitor = currentMonitor();
                        try {
                            componentMonitor.invoking(method, componentInstance);
                            long startTime = System.currentTimeMillis();
                            Object object = method.invoke(componentInstance, args);
                            componentMonitor.invoked(method, componentInstance, System.currentTimeMillis() - startTime);
                            return object;
                        } catch (final InvocationTargetException ite) {
                            componentMonitor.invocationFailed(method, componentInstance, ite);
                            throw ite.getTargetException();
                        }
                    }
                });
    }

    private Class[] verifyInterfacesOnly(Class[] classes) {
        for (int i = 0; i < classes.length; i++) {
            if(!classes[i].isInterface()) {
                throw new PicoIntrospectionException("Class keys must be interfaces. " + classes[i] + " is not an interface.");
            }
        }
        return classes;
    }

}
