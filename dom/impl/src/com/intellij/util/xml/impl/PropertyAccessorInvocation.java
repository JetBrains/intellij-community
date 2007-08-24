/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.JavaMethod;
import com.intellij.util.ArrayUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.ArrayList;

/**
 * @author peter
*/
class PropertyAccessorInvocation implements Invocation {
  final int myLastElement;
  private final JavaMethod[] myMethods;

  public PropertyAccessorInvocation(final JavaMethod[] methods) {
    myMethods = methods;
    myLastElement = myMethods.length - 1;
  }

  public final Object invoke(final DomInvocationHandler<?> handler, final Object[] args) throws Throwable {
    return invoke(0, handler.getProxy());
  }

  private Object invoke(final int i, final Object object) throws IllegalAccessException, InvocationTargetException {
    final Object o = myMethods[i].invoke(object, ArrayUtil.EMPTY_OBJECT_ARRAY);
    if (i == myLastElement) return o;

    if (o instanceof List) {
      List<Object> result = new ArrayList<Object>();
      for (Object o1 : (List)o) {
        result.add(invoke(i + 1, o1));
      }
      return result;
    }
    return invoke(i + 1, o);
  }
}
