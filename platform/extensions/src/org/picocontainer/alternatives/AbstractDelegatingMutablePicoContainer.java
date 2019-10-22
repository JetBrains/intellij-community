/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by the committers                                           *
 *****************************************************************************/
package org.picocontainer.alternatives;

import org.picocontainer.*;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * @author Paul Hammant
 * @version $Revision: 2230 $
 */
public abstract class AbstractDelegatingMutablePicoContainer implements MutablePicoContainer, Serializable {

  private MutablePicoContainer delegate;

  public AbstractDelegatingMutablePicoContainer(MutablePicoContainer delegate) {
    this.delegate = delegate;
  }

  protected MutablePicoContainer getDelegate() {
    return delegate;
  }

  @Override
  public ComponentAdapter registerComponentImplementation(Object componentKey, Class componentImplementation)
    throws PicoRegistrationException {
    return delegate.registerComponentImplementation(componentKey, componentImplementation);
  }

  @Override
  public ComponentAdapter registerComponentImplementation(Object componentKey, Class componentImplementation, Parameter[] parameters)
    throws PicoRegistrationException {
    return delegate.registerComponentImplementation(componentKey, componentImplementation, parameters);
  }


  @Override
  public ComponentAdapter registerComponentImplementation(Class componentImplementation) throws PicoRegistrationException {
    return delegate.registerComponentImplementation(componentImplementation);
  }

  @Override
  public ComponentAdapter registerComponentInstance(Object componentInstance) throws PicoRegistrationException {
    return delegate.registerComponentInstance(componentInstance);
  }

  @Override
  public ComponentAdapter registerComponentInstance(Object componentKey, Object componentInstance) throws PicoRegistrationException {
    return delegate.registerComponentInstance(componentKey, componentInstance);
  }

  @Override
  public ComponentAdapter registerComponent(ComponentAdapter componentAdapter) throws PicoRegistrationException {
    return delegate.registerComponent(componentAdapter);
  }

  @Override
  public ComponentAdapter unregisterComponent(Object componentKey) {
    return delegate.unregisterComponent(componentKey);
  }

  @Override
  public ComponentAdapter unregisterComponentByInstance(Object componentInstance) {
    return delegate.unregisterComponentByInstance(componentInstance);
  }

  @Override
  public Object getComponentInstance(Object componentKey) {
    return delegate.getComponentInstance(componentKey);
  }

  @Override
  public Object getComponentInstanceOfType(Class componentType) {
    return delegate.getComponentInstanceOfType(componentType);
  }

  @Override
  public List getComponentInstances() {
    return delegate.getComponentInstances();
  }

  @Override
  public PicoContainer getParent() {
    return delegate.getParent();
  }

  @Override
  public ComponentAdapter getComponentAdapter(Object componentKey) {
    return delegate.getComponentAdapter(componentKey);
  }

  @Override
  public ComponentAdapter getComponentAdapterOfType(Class componentType) {
    return delegate.getComponentAdapterOfType(componentType);
  }

  @Override
  public Collection getComponentAdapters() {
    return delegate.getComponentAdapters();
  }

  @Override
  public List getComponentAdaptersOfType(Class componentType) {
    return delegate.getComponentAdaptersOfType(componentType);
  }

  @Override
  public void dispose() {
    delegate.dispose();
  }

  @Override
  public boolean addChildContainer(PicoContainer child) {
    return delegate.addChildContainer(child);
  }

  @Override
  public boolean removeChildContainer(PicoContainer child) {
    return delegate.removeChildContainer(child);
  }

  @Override
  public void accept(PicoVisitor visitor) {
    delegate.accept(visitor);
  }

  @Override
  public List getComponentInstancesOfType(Class type) throws PicoException {
    return delegate.getComponentInstancesOfType(type);
  }

  public boolean equals(Object obj) {
    // required to make it pass on both jdk 1.3 and jdk 1.4. Btw, what about overriding hashCode()? (AH)
    return delegate.equals(obj) || this == obj;
  }
}
