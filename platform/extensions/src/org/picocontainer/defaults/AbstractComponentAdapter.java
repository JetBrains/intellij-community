/*
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
 *****************************************************************************/
package org.picocontainer.defaults;

import org.picocontainer.ComponentAdapter;

/**
 * Base class for a ComponentAdapter with general functionality.
 * This implementation provides basic checks for a healthy implementation of a ComponentAdapter.
 * It does not allow to use <code>null</code> for the component key or the implementation,
 * ensures that the implementation is a concrete class and that the key is assignable from the
 * implementation if the key represents a type.
 *
 * @author Paul Hammant
 * @author Aslak Helles&oslash;y
 * @author Jon Tirs&eacute;n
 * @version $Revision: 2654 $
 * @since 1.0
 */
public abstract class AbstractComponentAdapter implements ComponentAdapter {
  private final Object componentKey;
  private final Class<?> componentImplementation;

  protected AbstractComponentAdapter(Object componentKey, Class<?> componentImplementation) {
    if (componentImplementation == null) {
      throw new NullPointerException("componentImplementation");
    }
    if (componentKey instanceof Class) {
      Class<?> componentType = (Class<?>)componentKey;
      if (!componentType.isAssignableFrom(componentImplementation)) {
        throw new AssignabilityRegistrationException(componentType, componentImplementation);
      }
    }

    this.componentKey = componentKey;
    this.componentImplementation = componentImplementation;
  }

  @Override
  public final Object getComponentKey() {
    if (componentKey == null) {
      throw new NullPointerException("componentKey");
    }
    return componentKey;
  }

  @Override
  public final Class<?> getComponentImplementation() {
    return componentImplementation;
  }

  public final String toString() {
    return getClass().getName() + "[" + getComponentKey() + "]";
  }
}
