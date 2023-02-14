/*
 * Copyright (c) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Idea by Rachel Davies, Original code by Aslak Hellesoy and Paul Hammant
 */

package com.intellij.util.pico;

import org.jetbrains.annotations.NonNls;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Aslak Helles&oslash;y
 * @author J&ouml;rg Schaible
 * @version $Revision: 1801 $
 */
final class CyclicDependencyException extends PicoIntrospectionException {
    private final List stack;

    /**
     * @since 1.1
     */
    CyclicDependencyException(Class element) {
        super((Throwable)null);
        this.stack = new LinkedList();
        push(element);
    }

    /**
     * @since 1.1
     */
    public void push(Class element) {
        stack.add(element);
    }

    public Class[] getDependencies() {
        return (Class[]) stack.toArray(new Class[stack.size()]);
    }

    @Override
    public @NonNls String getMessage() {
        return "Cyclic dependency: " + stack.toString();
    }
}
