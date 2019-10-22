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

import org.picocontainer.PicoIntrospectionException;

import java.util.Collection;

public class TooManySatisfiableConstructorsException extends PicoIntrospectionException {

    private Class forClass;
    private Collection constructors;

    public TooManySatisfiableConstructorsException(Class forClass, Collection constructors) {
        super( "Too many satisfiable constructors:" + constructors.toString());
        this.forClass = forClass;
        this.constructors = constructors;
    }

    public Class getForImplementationClass() {
        return forClass;
    }

    public Collection getConstructors() {
        return constructors;
    }
}
