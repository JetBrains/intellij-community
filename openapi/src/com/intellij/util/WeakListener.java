/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public abstract class WeakListener<Src, Listener> implements InvocationHandler{
  private final WeakReference<Listener> myDelegate;
  private Src mySource;

  protected WeakListener(Src source, Class<Listener> listenerInterface, Listener listenerImpl) {
    mySource = source;
    myDelegate = new WeakReference<Listener>(listenerImpl);
    final ClassLoader classLoader = listenerImpl.getClass().getClassLoader();
    final Listener proxy = (Listener)Proxy.newProxyInstance(classLoader, new Class[]{listenerInterface}, this);
    addListener(source, proxy);
  }

  protected abstract void addListener(Src source, Listener listener);

  protected abstract void removeListener(Src source, Listener listener);

  public final Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
    final Listener listenerImplObject = myDelegate.get();
    if (listenerImplObject == null) { // already collected
      removeListener(mySource, (Listener)proxy);
      mySource = null;
      return null;
    }
    return method.invoke(listenerImplObject, params);
  }
}
