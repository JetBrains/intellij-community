// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger.coroutines;

import java.util.ArrayList;
import java.util.List;

public final class CoroutinesDebugHelper {

  private static final String GET_CALLER_FRAME_METHOD = "getCallerFrame";
  private static final String DUMP_COROUTINES_INFO_METHOD = "dumpCoroutinesInfo";
  private static final String GET_LAST_OBSERVED_THREAD_METHOD = "getLastObservedThread";
  private static final String GET_SEQUENCE_NUMBER_METHOD = "getSequenceNumber";
  private static final String COROUTINE_OWNER_CLASS = "CoroutineOwner";
  private static final String DEBUG_COROUTINE_INFO_FIELD = "info";
  private static final String SEQUENCE_NUMBER_FIELD = "sequenceNumber";

  public static long[] getCoroutinesRunningOnCurrentThread(Object debugProbes, Thread currentThread) throws ReflectiveOperationException {
    List<Long> coroutinesIds = new ArrayList<>();
    List infos = (List)invoke(debugProbes, DUMP_COROUTINES_INFO_METHOD);
    for (Object info : infos) {
      if (invoke(info, GET_LAST_OBSERVED_THREAD_METHOD) == currentThread) {
        coroutinesIds.add((Long)invoke(info, GET_SEQUENCE_NUMBER_METHOD));
      }
    }
    long[] res = new long[coroutinesIds.size()];
    for (int i = 0; i < res.length; i++) {
      res[i] = coroutinesIds.get(i).longValue();
    }
    return res;
  }

  public static long tryGetContinuationId(Object continuation) throws ReflectiveOperationException {
    Object rootContinuation = getCoroutineOwner(continuation);
    if (rootContinuation.getClass().getSimpleName().contains(COROUTINE_OWNER_CLASS)) {
      Object debugCoroutineInfo = getField(rootContinuation, DEBUG_COROUTINE_INFO_FIELD);
      return (long) getField(debugCoroutineInfo, SEQUENCE_NUMBER_FIELD);
    }
    return -1;
  }

  // This method tries to extract CoroutineOwner as a root coroutine frame,
  // it is invoked when kotlinx-coroutines debug agent is enabled.
  private static Object getCoroutineOwner(Object stackFrame) throws ReflectiveOperationException {
    if (stackFrame.getClass().getSimpleName().equals(COROUTINE_OWNER_CLASS)) return stackFrame;
    Object parentFrame = invoke(stackFrame, GET_CALLER_FRAME_METHOD);
    return (parentFrame != null) ? getCoroutineOwner(parentFrame) : stackFrame;
  }

  private static Object getField(Object object, String fieldName) throws ReflectiveOperationException {
    return object.getClass().getField(fieldName).get(object);
  }

  private static Object invoke(Object object, String methodName) throws ReflectiveOperationException {
    return object.getClass().getMethod(methodName).invoke(object);
  }
}