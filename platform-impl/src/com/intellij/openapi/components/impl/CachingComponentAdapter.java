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
  private boolean delegateHasLifecylce;
  private final Object lock = new Object();

  public CachingComponentAdapter(ComponentAdapter delegate) {
    super(delegate);
    disposed = false;
    started = false;
    delegateHasLifecylce =
      delegate instanceof LifecycleStrategy && ((LifecycleStrategy)delegate).hasLifecycle(delegate.getComponentImplementation());
  }

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
  public void dispose(PicoContainer container) {
    if (delegateHasLifecylce) {
      if (disposed) throw new IllegalStateException("Already disposed");
      dispose(getComponentInstance(container));
      disposed = true;
    }
  }

  public boolean hasLifecycle() {
    return delegateHasLifecylce;
  }

}
