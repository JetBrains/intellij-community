/*****************************************************************************
 * Copyright (c) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Idea by Rachel Davies, Original code by Aslak Hellesoy and Paul Hammant   *
 *****************************************************************************/

package org.picocontainer.defaults;

import org.picocontainer.ComponentAdapter;
import org.picocontainer.Parameter;
import org.picocontainer.PicoIntrospectionException;

import java.io.Serializable;

/**
 * Creates instances of {@link ConstructorInjectionComponentAdapter} decorated by
 * {@link CachingComponentAdapter}.
 *
 * @author Jon Tirs&eacute;n
 * @author Aslak Helles&oslash;y
 * @version $Revision: 2779 $
 */
public final class DefaultComponentAdapterFactory implements ComponentAdapterFactory, Serializable {
  private final LifecycleStrategy lifecycleStrategy;

  public DefaultComponentAdapterFactory() {
    this.lifecycleStrategy = new DefaultLifecycleStrategy();
  }

  @Override
  public ComponentAdapter createComponentAdapter(Object componentKey, Class componentImplementation, Parameter[] parameters)
    throws PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {
    return new CachingComponentAdapter(new ConstructorInjectionComponentAdapter(componentKey, componentImplementation, parameters, false, lifecycleStrategy));
  }

  public static ComponentAdapter createAdapter(Object componentKey, Class<?> componentImplementation, Parameter[] parameters)
    throws PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {
    return new CachingComponentAdapter(new ConstructorInjectionComponentAdapter(componentKey, componentImplementation, parameters, false, new DefaultLifecycleStrategy()));
  }
}
