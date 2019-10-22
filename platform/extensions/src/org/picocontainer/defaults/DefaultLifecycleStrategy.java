/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *****************************************************************************/
package org.picocontainer.defaults;

import org.picocontainer.ComponentMonitor;
import org.picocontainer.Disposable;

import java.lang.reflect.Method;

/**
 * Default lifecycle strategy.  Starts and stops component if Startable,
 * and disposes it if Disposable.
 *
 * @author Mauro Talevi
 * @author J&ouml;rg Schaible
 * @see Startable
 * @see Disposable
 */
public class DefaultLifecycleStrategy extends AbstractMonitoringLifecycleStrategy {

  private static Method dispose;

  static {
    try {
      dispose = Disposable.class.getMethod("dispose", (Class[])null);
    }
    catch (NoSuchMethodException ignored) {
    }
  }

  public DefaultLifecycleStrategy(ComponentMonitor monitor) {
    super(monitor);
  }

  @Override
  public void start(Object component) {
  }

  @Override
  public void stop(Object component) {
  }

  @Override
  public void dispose(Object component) {
    if (component instanceof Disposable) {
      long str = System.currentTimeMillis();
      currentMonitor().invoking(dispose, component);
      try {
        ((Disposable)component).dispose();
        currentMonitor().invoked(dispose, component, System.currentTimeMillis() - str);
      }
      catch (RuntimeException cause) {
        currentMonitor().lifecycleInvocationFailed(dispose, component, cause); // may re-throw
      }
    }
  }

  @Override
  public boolean hasLifecycle(Class type) {
    return Disposable.class.isAssignableFrom(type);
  }
}
