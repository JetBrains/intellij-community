/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
 *****************************************************************************/
package org.picocontainer;

import java.util.Collection;
import java.util.List;

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
   * @return an instantiated component, or <code>null</code> if no component has been registered for the specified
   * key.
   */
  Object getComponentInstance(Object componentKey);

  /**
   * Find a component instance matching the specified type.
   *
   * @param componentType the type of the component
   * @return an instantiated component matching the class, or <code>null</code> if no component has been registered
   * with a matching type
   * @throws PicoException if the instantiation of the component fails
   */
  Object getComponentInstanceOfType(Class componentType);

  /**
   * Retrieve the parent container of this container.
   *
   * @return a {@link PicoContainer} instance, or <code>null</code> if this container does not have a parent.
   */
  PicoContainer getParent();

  /**
   * Find a component adapter associated with the specified key. If a component adapter cannot be found in this
   * container, the parent container (if one exists) will be searched.
   *
   * @param componentKey the key that the component was registered with.
   * @return the component adapter associated with this key, or <code>null</code> if no component has been
   * registered for the specified key.
   */
  ComponentAdapter getComponentAdapter(Object componentKey);

  /**
   * Find a component adapter associated with the specified type. If a component adapter cannot be found in this
   * container, the parent container (if one exists) will be searched.
   *
   * @param componentType the type of the component.
   * @return the component adapter associated with this class, or <code>null</code> if no component has been
   * registered for the specified key.
   */
  ComponentAdapter getComponentAdapterOfType(Class componentType);

  /**
   * Retrieve all the component adapters inside this container. The component adapters from the parent container are
   * not returned.
   *
   * @return a collection containing all the {@link ComponentAdapter}s inside this container. The collection will not
   * be modifiable.
   * @see #getComponentAdaptersOfType(Class) a variant of this method which returns the component adapters inside this
   * container that are associated with the specified type.
   */
  Collection<ComponentAdapter> getComponentAdapters();

  /**
   * Retrieve all component adapters inside this container that are associated with the specified type. The component
   * adapters from the parent container are not returned.
   *
   * @param componentType the type of the components.
   * @return a collection containing all the {@link ComponentAdapter}s inside this container that are associated with
   * the specified type. Changes to this collection will not be reflected in the container itself.
   */
  List<ComponentAdapter> getComponentAdaptersOfType(Class componentType);
}
