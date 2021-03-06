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
 * @see MutablePicoContainer an extension of the PicoContainer interface which allows you to modify the contents of the
 * container.
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

  /**
   * @param container the {@link PicoContainer}, that is used to resolve any possible dependencies of the instance.
   * @return the component instance.
   * @throws PicoInitializationException if the component could not be instantiated.
   * @throws PicoIntrospectionException  if the component has dependencies which could not be resolved, or
   *                                     instantiation of the component lead to an ambiguous situation within the
   *                                     container.
   */
  Object getComponentInstance(PicoContainer container);
}
