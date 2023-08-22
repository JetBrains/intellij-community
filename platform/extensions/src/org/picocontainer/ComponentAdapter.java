/*
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
*/
package org.picocontainer;

/**
 * A component adapter is responsible for providing a specific component instance. An instance of an implementation of
 * this interface is used inside a {@link PicoContainer} for every registered component or instance.  Each
 * <code>ComponentAdapter</code> instance has to have a key which is unique within that container. The key itself is
 * either a class type (normally an interface) or an identifier.
 *
 * @author Jon Tirs&eacute;n
 * @author Paul Hammant
 * @author Aslak Helles&oslash;y
 */
public interface ComponentAdapter {
  /**
   * Retrieve the key associated with the component.
   *
   * @return the component's key. Should either be a class type (normally an interface) or an identifier that is
   * unique (within the scope of the current PicoContainer).
   */
  Object getComponentKey();

  /**
   * Retrieve the class of the component.
   *
   * @return the component's implementation class. Should normally be a concrete class (ie, a class that can be
   * instantiated).
   */
  Class<?> getComponentImplementation();

  Object getComponentInstance();
}
