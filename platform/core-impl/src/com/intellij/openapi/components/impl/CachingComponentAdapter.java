/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components.impl;

import org.picocontainer.ComponentAdapter;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoInitializationException;
import org.picocontainer.PicoIntrospectionException;
import org.picocontainer.defaults.AssignabilityRegistrationException;
import org.picocontainer.defaults.DecoratingComponentAdapter;
import org.picocontainer.defaults.LifecycleStrategy;
import org.picocontainer.defaults.NotConcreteRegistrationException;

/**
 * @author mike
 */
public class CachingComponentAdapter extends DecoratingComponentAdapter {
  private volatile Object cached;
  private boolean disposed;
  private boolean started;
  private final boolean delegateHasLifecylce;
  private final Object lock = new Object();

  public CachingComponentAdapter(ComponentAdapter delegate) {
    super(delegate);
    disposed = false;
    started = false;
    delegateHasLifecylce =
      delegate instanceof LifecycleStrategy && ((LifecycleStrategy)delegate).hasLifecycle(delegate.getComponentImplementation());
  }

  @Override
  public Object getComponentInstance(PicoContainer container)
    throws PicoInitializationException, PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {

    Object o = cached;
    if (o != null) return o;
    synchronized (lock) {
      o = cached;
      if (o != null) return o;
      o = super.getComponentInstance(container);
      cached = o;
    }
    return o;
  }

  /**
   * Starts the cached component instance
   * {@inheritDoc}
   */
  @Override
  public void start(PicoContainer container) {
    if (delegateHasLifecylce) {
      if (disposed) throw new IllegalStateException("Already disposed");
      if (started) throw new IllegalStateException("Already started");
      start(getComponentInstance(container));
      started = true;
    }
  }

  /**
   * Stops the cached component instance
   * {@inheritDoc}
   */
  @Override
  public void stop(PicoContainer container) {
    if (delegateHasLifecylce) {
      if (disposed) throw new IllegalStateException("Already disposed");
      if (!started) throw new IllegalStateException("Not started");
      stop(getComponentInstance(container));
      started = false;
    }
  }

  /**
   * Disposes the cached component instance
   * {@inheritDoc}
   */
  @Override
  public void dispose(PicoContainer container) {
    if (delegateHasLifecylce) {
      if (disposed) throw new IllegalStateException("Already disposed");
      dispose(getComponentInstance(container));
      disposed = true;
    }
  }

  @Override
  public boolean hasLifecycle() {
    return delegateHasLifecylce;
  }

}
