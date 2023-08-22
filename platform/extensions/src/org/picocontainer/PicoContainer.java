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
   * @deprecated Use ComponentManager directly instead.
   */
  @Deprecated
  Object getComponentInstance(@NotNull Object componentKey);

  /**
   * @deprecated Use extension points instead.
   */
  @Deprecated
  Object getComponentInstanceOfType(@NotNull Class<?> componentType);
}
