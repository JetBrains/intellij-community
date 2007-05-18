/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Factory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.MergedObject;
import com.intellij.util.xml.StableElement;
import net.sf.cglib.proxy.InvocationHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
class StableInvocationHandler<T extends DomElement> implements InvocationHandler, StableElement {
  private T myOldValue;
  private T myCachedValue;
  private Set<Class> myClasses;
  private final Factory<T> myProvider;

  public StableInvocationHandler(final T initial, final Factory<T> provider) {
    myProvider = provider;
    myCachedValue = initial;
    myOldValue = initial;
    final Class superClass = initial.getClass().getSuperclass();
    final Set<Class> classes = new HashSet<Class>();
    classes.addAll(Arrays.asList(initial.getClass().getInterfaces()));
    ContainerUtil.addIfNotNull(superClass, classes);
    classes.remove(MergedObject.class);
    myClasses = classes;
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

    if (AdvancedProxy.FINALIZE_METHOD.equals(method)) {
      return null;
    }

    if (isNotValid(myCachedValue)) {
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
      myCachedValue = t;
    }
  }

  public final void invalidate() {
    if (!isNotValid(myCachedValue)) {
      myCachedValue = null;
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
    if (t == null || !t.isValid()) return true;
    for (final Class aClass : myClasses) {
      if (!aClass.isInstance(t)) return true;
    }
    return false;
  }
}
