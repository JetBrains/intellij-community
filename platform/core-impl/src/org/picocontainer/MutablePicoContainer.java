/*
 * Copyright (c) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Idea by Rachel Davies, Original code by various                           *
*/
package org.picocontainer;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author Paul Hammant
 * @author Aslak Helles&oslash;y
 * @author Jon Tirs&eacute;n
 */
@TestOnly
public interface MutablePicoContainer extends PicoContainer {
  /**
   * @deprecated Use services.
   */
  @Deprecated
  ComponentAdapter registerComponentImplementation(@NotNull Object componentKey, @NotNull Class<?> componentImplementation);

  /**
   * @deprecated Use services.
   */
  @Deprecated
  default ComponentAdapter registerComponentImplementation(@NotNull Class<?> componentImplementation) {
    return registerComponentImplementation(componentImplementation, componentImplementation);
  }

  /**
   * @deprecated Use services.
   */
  @ApiStatus.Internal
  @Deprecated
  ComponentAdapter registerComponentInstance(Object componentKey, Object componentInstance);

  /**
   * Unregister a component by key.
   *
   * @param componentKey key of the component to unregister.
   * @return the ComponentAdapter that was associated with this component.
   */
  ComponentAdapter unregisterComponent(Object componentKey);

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
