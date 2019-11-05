/*****************************************************************************
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
import org.picocontainer.Parameter;
import org.picocontainer.PicoIntrospectionException;

/**
 * @author Jon Tirs&eacute;n
 * @author Mauro Talevi
 * @version $Revision: 2230 $
 */
public interface ComponentAdapterFactory {

  /**
   * Create a new component adapter based on the specified arguments.
   *
   * @param componentKey            the key to be associated with this adapter. This value should be returned
   *                                from a call to {@link ComponentAdapter#getComponentKey()} on the created adapter.
   * @param componentImplementation the implementation class to be associated with this adapter.
   *                                This value should be returned from a call to
   *                                {@link ComponentAdapter#getComponentImplementation()} on the created adapter. Should not
   *                                be null.
   * @param parameters              additional parameters to use by the component adapter in constructing
   *                                component instances. These may be used, for example, to make decisions about the
   *                                arguments passed into the component constructor. These should be considered hints; they
   *                                may be ignored by some implementations. May be null, and may be of zero length.
   * @return a new component adapter based on the specified arguments. Should not return null.
   * @throws PicoIntrospectionException         if the creation of the component adapter results in a
   *                                            {@link PicoIntrospectionException}.
   * @throws AssignabilityRegistrationException if the creation of the component adapter results in a
   *                                            {@link AssignabilityRegistrationException}.
   * @throws NotConcreteRegistrationException   if the creation of the component adapter results in a
   *                                            {@link NotConcreteRegistrationException}.
   */
  ComponentAdapter createComponentAdapter(Object componentKey,
                                          Class componentImplementation,
                                          Parameter[] parameters)
    throws PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException;
}
