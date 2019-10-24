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

import org.picocontainer.*;

/**
 * <p>
 * {@link ComponentAdapter} implementation that caches the component instance.
 * </p>
 * <p>
 * This adapter supports components with a lifecycle, as it is a {@link LifecycleManager lifecycle manager}
 * which will apply the delegate's {@link LifecycleStrategy lifecycle strategy} to the cached component instance.
 * The lifecycle state is maintained so that the component instance behaves in the expected way:
 * it can't be started if already started, it can't be started or stopped if disposed, it can't
 * be stopped if not started, it can't be disposed if already disposed.
 * </p>
 *
 * @author Mauro Talevi
 * @version $Revision: 2827 $
 */
public final class CachingComponentAdapter extends DecoratingComponentAdapter implements LifecycleManager {
  private final ObjectReference instanceReference;
  private boolean disposed;
  private final boolean started;
  private final boolean delegateHasLifecylce;

  public CachingComponentAdapter(ComponentAdapter delegate) {
    this(delegate, new SimpleReference());
  }

  public CachingComponentAdapter(ComponentAdapter delegate, ObjectReference instanceReference) {
    super(delegate);
    this.instanceReference = instanceReference;
    this.disposed = false;
    this.started = false;
    this.delegateHasLifecylce = delegate instanceof LifecycleStrategy
                                && ((LifecycleStrategy)delegate).hasLifecycle(delegate.getComponentImplementation());
  }

  @Override
  public Object getComponentInstance(PicoContainer container)
    throws PicoInitializationException, PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {
    Object instance = instanceReference.get();
    if (instance == null) {
      instance = super.getComponentInstance(container);
      instanceReference.set(instance);
    }
    return instance;
  }

  /**
   * Flushes the cache.
   * If the component instance is started is will stop and dispose it before
   * flushing the cache.
   */
  public void flush() {
    Object instance = instanceReference.get();
    if (instance != null && delegateHasLifecylce && started) {
      dispose(instance);
    }
    instanceReference.set(null);
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
