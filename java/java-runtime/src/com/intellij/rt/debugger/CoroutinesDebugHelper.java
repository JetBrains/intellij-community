// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"KotlinInternalInJava", "UnnecessaryFullyQualifiedName"})
public final class CoroutinesDebugHelper {
  public static long[] getCoroutinesRunningOnCurrentThread(kotlinx.coroutines.debug.internal.DebugProbesImpl instance) {
    Thread currentThread = Thread.currentThread();
    List<Long> coroutinesIds = new ArrayList<>();
    for (kotlinx.coroutines.debug.internal.DebugCoroutineInfo info : instance.dumpCoroutinesInfo()) {
      if (info.getLastObservedThread() == currentThread) {
        coroutinesIds.add(info.getSequenceNumber());
      }
    }
    long[] res = new long[coroutinesIds.size()];
    for (int i = 0; i < res.length; i++) {
      res[i] = coroutinesIds.get(i).longValue();
    }
    return res;
  }
}
