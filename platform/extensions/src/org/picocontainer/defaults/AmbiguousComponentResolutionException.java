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

import org.jetbrains.annotations.NonNls;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.PicoIntrospectionException;

import java.util.Arrays;

/**
 * Exception that is thrown as part of the introspection. Raised if a PicoContainer cannot resolve a
 * type dependency because the registered {@link ComponentAdapter}s are not
 * distinct.
 *
 * @author Paul Hammant
 * @author Aslak Helles&oslash;y
 * @author Jon Tirs&eacute;n
 * @since 1.0
 */
public final class AmbiguousComponentResolutionException extends PicoIntrospectionException {
  private Class<?> component;
  private final Class<?> ambiguousDependency;
  private final Object[] ambiguousComponentKeys;

  /**
   * Construct a new exception with the ambigous class type and the ambiguous component keys.
   *
   * @param ambiguousDependency the unresolved dependency type
   * @param componentKeys       the ambiguous keys.
   */
  public AmbiguousComponentResolutionException(Class ambiguousDependency, Object[] componentKeys) {
    super("");
    this.ambiguousDependency = ambiguousDependency;
    this.ambiguousComponentKeys = componentKeys.clone();
  }

  /**
   * @return Returns a string containing the unresolved class type and the ambiguous keys.
   */
  @Override
  public @NonNls String getMessage() {
    return component +
           " has ambiguous dependency on " +
           ambiguousDependency +
           ", " +
           "resolves to multiple classes: " +
           Arrays.asList(getAmbiguousComponentKeys());
  }

  /**
   * @return Returns the ambiguous component keys as array.
   */
  public Object[] getAmbiguousComponentKeys() {
    return ambiguousComponentKeys;
  }

  public void setComponent(Class component) {
    this.component = component;
  }
}
