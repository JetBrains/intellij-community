/*
 * Copyright (c) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Idea by Rachel Davies, Original code by Aslak Hellesoy and Paul Hammant
 */
package org.picocontainer.defaults;

import org.picocontainer.PicoIntrospectionException;

import java.lang.reflect.Constructor;
import java.util.Collection;

public final class TooManySatisfiableConstructorsException extends PicoIntrospectionException {
  private final Collection<Constructor<?>> constructors;

  public TooManySatisfiableConstructorsException(Collection<Constructor<?>> constructors) {
    super("Too many satisfiable constructors:" + constructors.toString());

    this.constructors = constructors;
  }

  public Collection<Constructor<?>> getConstructors() {
    return constructors;
  }
}
