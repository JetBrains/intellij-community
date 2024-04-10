// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger;

import java.util.ArrayList;
import java.util.List;

public final class CoroutinesDebugHelper {
  public static long[] getCoroutinesRunningOnCurrentThread(Object debugProbes) throws ReflectiveOperationException {
    Thread currentThread = Thread.currentThread();
    List<Long> coroutinesIds = new ArrayList<>();
    List infos = (List)invoke(debugProbes, "dumpCoroutinesInfo");
    for (Object info : infos) {
      if (invoke(info, "getLastObservedThread") == currentThread) {
        coroutinesIds.add((Long)invoke(info, "getSequenceNumber"));
      }
    }
    long[] res = new long[coroutinesIds.size()];
    for (int i = 0; i < res.length; i++) {
      res[i] = coroutinesIds.get(i).longValue();
    }
    return res;
  }

  private static Object invoke(Object object, String methodName) throws ReflectiveOperationException {
    return object.getClass().getMethod(methodName).invoke(object);
  }
}