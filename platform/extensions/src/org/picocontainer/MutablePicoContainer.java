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

import org.jetbrains.annotations.NotNull;

/**
 * @author Paul Hammant
 * @author Aslak Helles&oslash;y
 * @author Jon Tirs&eacute;n
 */
public interface MutablePicoContainer extends PicoContainer {
  /**
   * @deprecated Do not use.
   */
  @Deprecated
  ComponentAdapter registerComponentImplementation(@NotNull Object componentKey, @NotNull Class<?> componentImplementation);

  /**
   * @deprecated Do not use.
   */
  @Deprecated
  ComponentAdapter registerComponentImplementation(@NotNull Class<?> componentImplementation);

  /**
   * Register an arbitrary object. The class of the object will be used as a key. Calling this method is equivalent to
   * calling     * <code>registerComponentImplementation(componentImplementation, componentImplementation)</code>.
   *
   * @param componentInstance
   * @return the ComponentAdapter that has been associated with this component. In the majority of cases, this return
   * value can be safely ignored, as one of the <code>getXXX()</code> methods of the
   * {@link PicoContainer} interface can be used to retrieve a reference to the component later on.
   * @throws PicoRegistrationException if registration fails.
   */
  ComponentAdapter registerComponentInstance(Object componentInstance);

  /**
   * Register an arbitrary object as a component in the container. This is handy when other components in the same
   * container have dependencies on this kind of object, but where letting the container manage and instantiate it is
   * impossible.
   * <p/>
   * @return the ComponentAdapter that has been associated with this component. In the majority of cases, this return
   * value can be safely ignored, as one of the <code>getXXX()</code> methods of the
   * {@link PicoContainer} interface can be used to retrieve a reference to the component later on.
   * @throws PicoRegistrationException if registration fails.
   */
  ComponentAdapter registerComponentInstance(Object componentKey, Object componentInstance);

  /**
   * Register a component via a ComponentAdapter. Use this if you need fine grained control over what
   * ComponentAdapter to use for a specific component.
   *
   * @param componentAdapter the adapter
   * @return the same adapter that was passed as an argument.
   * @throws PicoRegistrationException if registration fails.
   */
  ComponentAdapter registerComponent(ComponentAdapter componentAdapter);

  /**
   * Unregister a component by key.
   *
   * @param componentKey key of the component to unregister.
   * @return the ComponentAdapter that was associated with this component.
   */
  ComponentAdapter unregisterComponent(Object componentKey);
}
