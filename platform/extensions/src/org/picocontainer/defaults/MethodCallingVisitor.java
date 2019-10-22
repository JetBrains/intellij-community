/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *****************************************************************************/
package org.picocontainer.defaults;

import org.picocontainer.PicoContainer;
import org.picocontainer.PicoIntrospectionException;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


/**
 * A PicoVisitor implementation, that calls methods on the components of a specific type.
 *
 * @author Aslak Helles&oslash;y
 * @author J&ouml;rg Schaible
 * @since 1.2
 */
public class MethodCallingVisitor extends TraversalCheckingVisitor implements Serializable {

    // TODO: we must serialize method with read/writeObject ... and are our parent serializable ???
    private transient Method method;
    private final Object[] arguments;
    private final Class type;
    private final boolean visitInInstantiationOrder;
    private final List componentInstances;

    /**
     * Construct a MethodCallingVisitor for a method with arguments.
     *
     * @param method the {@link Method} to invoke
     * @param ofType the type of the components, that will be invoked
     * @param visitInInstantiationOrder <code>true</code> if components are visited in instantiation order
     * @param arguments the arguments for the method invocation (may be <code>null</code>)
     * @throws NullPointerException if <tt>method</tt>, or <tt>ofType</tt> is <code>null</code>
     * @since 1.2
     */
    public MethodCallingVisitor(Method method, Class ofType, Object[] arguments, boolean visitInInstantiationOrder) {
        if (method == null) {
            throw new NullPointerException();
        }
        this.method = method;
        this.arguments = arguments;
        this.type = ofType;
        this.visitInInstantiationOrder = visitInInstantiationOrder;
        this.componentInstances = new ArrayList();
    }

    /**
     * Construct a MethodCallingVisitor for standard methods visiting the component in instantiation order.
     *
     * @param method the method to invoke
     * @param ofType the type of the components, that will be invoked
     * @param arguments the arguments for the method invocation (may be <code>null</code>)
     * @throws NullPointerException if <tt>method</tt>, or <tt>ofType</tt> is <code>null</code>
     * @since 1.2
     */
    public MethodCallingVisitor(Method method, Class ofType, Object[] arguments) {
        this(method, ofType, arguments, true);
    }

    @Override
    public Object traverse(Object node) {
        componentInstances.clear();
        try {
            super.traverse(node);
            if (!visitInInstantiationOrder) {
                Collections.reverse(componentInstances);
            }
            for (Iterator iterator = componentInstances.iterator(); iterator.hasNext();) {
                invoke(iterator.next());
            }
        } finally {
            componentInstances.clear();
        }
        return Void.TYPE;
    }

    @Override
    public void visitContainer(PicoContainer pico) {
        super.visitContainer(pico);
        componentInstances.addAll(pico.getComponentInstancesOfType(type));
    }

    protected Method getMethod() {
        return method;
    }

    protected Object[] getArguments() {
        return arguments;
    }

    protected void invoke(final Object[] targets) {
        for (int i = 0; i < targets.length; i++) {
            invoke(targets[i]);
        }
    }

    protected Object invoke(final Object target) {
        final Method method = getMethod();
        try {
            method.invoke(target, getArguments());
        } catch (IllegalArgumentException e) {
            throw new PicoIntrospectionException("Can't call " + method.getName() + " on " + target, e);
        } catch (IllegalAccessException e) {
            throw new PicoIntrospectionException("Can't call " + method.getName() + " on " + target, e);
        } catch (InvocationTargetException e) {
            throw new PicoIntrospectionException("Failed when calling " + method.getName() + " on " + target, e
                    .getTargetException());
        }
        return Void.TYPE;
    }
}
