// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.debugger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class DefaultMethodInvoker {

  // only methods without arguments for now
  public static Object invoke(Object obj, String name)
    throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

    Method method = obj.getClass().getMethod(name, null);
    if (method != null) {
      method.setAccessible(true);
      Object res = method.invoke(obj, null);
      method.setAccessible(false);
      return res;
    }
    return null;
  }
}
