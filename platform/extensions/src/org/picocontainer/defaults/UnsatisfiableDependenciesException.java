/*
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
*/
package org.picocontainer.defaults;

import org.jetbrains.annotations.NotNull;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoIntrospectionException;

import java.util.Set;

/**
 * Exception thrown when some of the component's dependencies are not satisfiable.
 *
 * @author Aslak Helles&oslash;y
 * @author Mauro Talevi
 */
public final class UnsatisfiableDependenciesException extends PicoIntrospectionException {
  public UnsatisfiableDependenciesException(ComponentAdapter instantiatingComponentAdapter,
                                            Set unsatisfiableDependencies, PicoContainer leafContainer) {
    super(instantiatingComponentAdapter.getComponentImplementation().getName() + " has unsatisfiable dependencies: "
          + unsatisfiableDependencies + " where " + leafContainer
          + " was the leaf container being asked for dependencies.");
  }

  public UnsatisfiableDependenciesException(@NotNull Class<?> componentImplementation,
                                            Class<?> unsatisfiedDependencyType, Set unsatisfiableDependencies,
                                            PicoContainer leafContainer) {
    super(componentImplementation.getName() + " has unsatisfied dependency: " + unsatisfiedDependencyType
          + " among unsatisfiable dependencies: " + unsatisfiableDependencies + " where " + leafContainer
          + " was the leaf container being asked for dependencies.");
  }
}
