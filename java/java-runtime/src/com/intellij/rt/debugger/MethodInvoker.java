// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class MethodInvoker {
  // TODO: may leak objects here
  static ThreadLocal<Object> returnValue = new ThreadLocal<>();

  public static Object invoke(Class<?> cls, Object obj, String name, Class<?>[] parameterTypes, Object[] args)
    throws Throwable {
    ArrayList<Method> methods = new ArrayList<>();
    // TODO: better collect methods lazily
    addMatchingMethods(methods, cls, name, parameterTypes);
    for (Method method : methods) {
      try {
        method.setAccessible(true);
        Object res = method.invoke(obj, args);
        returnValue.set(res); // to avoid gc for the result object
        return res;
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
      catch (IllegalAccessException | SecurityException e) {
        // try the next method
      }
      catch (Exception e) {
        // InaccessibleObjectException appeared in jdk 9
        if (!"java.lang.reflect.InaccessibleObjectException".equals(e.getClass().getName())) {
          throw e;
        }
        // try the next method
      }
    }
    throw new RuntimeException("Unable to execute the method " + name);
  }

  //TODO: avoid recursion
  private static void addMatchingMethods(List<Method> methods, Class<?> cls, String name, Class<?>[] parameterTypes) {
    try {
      methods.add(cls.getDeclaredMethod(name, parameterTypes));
    }
    catch (NoSuchMethodException ignored) {
    }
    Class<?> superclass = cls.getSuperclass();
    if (superclass != null) {
      addMatchingMethods(methods, superclass, name, parameterTypes);
    }
    for (Class<?> anInterface : cls.getInterfaces()) {
      addMatchingMethods(methods, anInterface, name, parameterTypes);
    }
  }
}
