/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.StableElement;
import com.intellij.openapi.util.Factory;
import net.sf.cglib.proxy.InvocationHandler;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/**
 * @author peter
*/
class StableInvocationHandler<T extends DomElement> implements InvocationHandler, StableElement {
  private T myOldValue;
  private T myCachedValue;
  private final Factory<T> myProvider;

  public StableInvocationHandler(final T initial, final Factory<T> provider) {
    myProvider = provider;
    myCachedValue = initial;
  }

  public final Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
    if (StableElement.class.equals(method.getDeclaringClass())) {
      try {
        return method.invoke(this, args);
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }

    if (isNotValid(myCachedValue)) {
      if (AdvancedProxy.FINALIZE_METHOD.equals(method)) {
        return null;
      }

      if (myCachedValue != null) {
        myOldValue = myCachedValue;
      }
      myCachedValue = myProvider.create();
      if (isNotValid(myCachedValue)) {
        if (myOldValue != null && Object.class.equals(method.getDeclaringClass())) {
          return method.invoke(myOldValue, args);
        }

        if ("isValid".equals(method.getName())) {
          return Boolean.FALSE;
        }
        throw new AssertionError("Calling methods on invalid value");
      }
    }

    try {
      return method.invoke(myCachedValue, args);
    }
    catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  public final void revalidate() {
    final T t = myProvider.create();
    if (!isNotValid(t) && !t.equals(myCachedValue)) {
      doInvalidate();
      myCachedValue = t;
    }
  }

  private void doInvalidate() {
    DomManagerImpl.getDomInvocationHandler(myCachedValue).detach(true);
  }

  public final void invalidate() {
    if (!isNotValid(myCachedValue)) {
      doInvalidate();
    }
  }

  public final DomElement getWrappedElement() {
    if (isNotValid(myCachedValue)) {
      myCachedValue = myProvider.create();
    }
    return myCachedValue;
  }

  public T getOldValue() {
    return myOldValue;
  }

  private boolean isNotValid(final T t) {
    return t == null || !t.isValid();
  }
}
