/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.impl.watch;

import com.sun.jdi.Method;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 13, 2006
 */
public class MethodsTracker {
  private final Map<Method, Integer> myMethodToOccurrenceMap = new HashMap<Method, Integer>();

  public final class MethodOccurrence {
    private final Method myMethod;
    private final int myIndex;

    private MethodOccurrence(Method method, int index) {
      myMethod = method;
      myIndex = index;
    }

    public Method getMethod() {
      return myMethod;
    }

    public int getIndex() {
      return getOccurrenceCount(myMethod) - myIndex;
    }

    public boolean isRecursive() {
      return getOccurrenceCount(myMethod) > 1;
    }
  }

  public MethodOccurrence getMethodOccurrence(Method method) {
    return new MethodOccurrence(method, assignOccurrenceIndex(method));
  }

  private int getOccurrenceCount(Method method) {
    if (method == null) {
      return 0;
    }
    final Integer integer = myMethodToOccurrenceMap.get(method);
    return integer != null? integer.intValue(): 0;
  }

  private int assignOccurrenceIndex(Method method) {
    if (method == null) {
      return 0;
    }
    final int count = getOccurrenceCount(method);
    myMethodToOccurrenceMap.put(method, count + 1);
    return count;
  }
}
