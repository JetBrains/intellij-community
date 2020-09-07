/*
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
*/
package org.picocontainer;

import org.jetbrains.annotations.NotNull;

/**
 * @author Paul Hammant
 * @author Aslak Helles&oslash;y
 * @author Jon Tirs&eacute;n
 */
public interface PicoContainer {
  /**
   * Retrieve a component instance registered with a specific key. If a component cannot be found in this container,
   * the parent container (if one exists) will be searched.
   *
   * @param componentKey the key that the component was registered with.
   * @return an instantiated component, or {@code null} if no component has been registered for the specified
   * key.
   */
  Object getComponentInstance(@NotNull Object componentKey);

  /**
   * Find a component instance matching the specified type.
   *
   * @param componentType the type of the component
   * @return an instantiated component matching the class, or {@code null} if no component has been registered
   * with a matching type
   * @throws PicoException if the instantiation of the component fails
   */
  Object getComponentInstanceOfType(@NotNull Class<?> componentType);

  /**
   * Find a component adapter associated with the specified key. If a component adapter cannot be found in this
   * container, the parent container (if one exists) will be searched.
   *
   * @param componentKey the key that the component was registered with.
   * @return the component adapter associated with this key, or {@code null} if no component has been
   * registered for the specified key.
   */
  ComponentAdapter getComponentAdapter(@NotNull Object componentKey);
}
