// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger.coroutines;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class CoroutinesDebugHelper {

  private static final String COROUTINE_OWNER_CLASS = "CoroutineOwner";

  public static long[] getCoroutinesRunningOnCurrentThread(Object debugProbes, Thread currentThread) throws ReflectiveOperationException {
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

  public static long tryGetContinuationId(Object continuation) throws ReflectiveOperationException {
    Object rootContinuation = getCoroutineOwner(continuation, true);
    if (rootContinuation.getClass().getSimpleName().contains(COROUTINE_OWNER_CLASS)) {
      Object debugCoroutineInfo = getField(rootContinuation, "info");
      return (long) getField(debugCoroutineInfo, "sequenceNumber");
    }
    return -1;
  }

  // This method tries to extract CoroutineOwner as a root coroutine frame,
  // it is invoked when kotlinx-coroutines debug agent is enabled.
  private static Object getCoroutineOwner(Object continuation, boolean checkForCoroutineOwner) throws ReflectiveOperationException {
    Method getCallerFrame = Class.forName("kotlin.coroutines.jvm.internal.CoroutineStackFrame", false, continuation.getClass().getClassLoader())
      .getDeclaredMethod("getCallerFrame");
    getCallerFrame.setAccessible(true);
    Object current = continuation;
    while (true) {
      if (checkForCoroutineOwner && current.getClass().getSimpleName().equals(COROUTINE_OWNER_CLASS)) return current;
      Object parentFrame = getCallerFrame.invoke(current);
      if ((parentFrame != null)) {
        current = parentFrame;
      } else {
        return current;
      }
    }
  }

  public static Object getRootContinuation(Object continuation) throws ReflectiveOperationException {
    return getCoroutineOwner(continuation, false);
  }

  public static Object getCallerFrame(Object continuation) throws ReflectiveOperationException {
    // This method extracts the caller frame of the given continuation.
    Class<?> coroutineStackFrame = Class.forName("kotlin.coroutines.jvm.internal.CoroutineStackFrame", false, continuation.getClass().getClassLoader());
    Method getCallerFrame = coroutineStackFrame.getDeclaredMethod("getCallerFrame");
    getCallerFrame.setAccessible(true);
    Object callerFrame = getCallerFrame.invoke(continuation);
    // In case the caller frame is the root CoroutineOwner completion added by the debug agent -> return the current continuation
    if (callerFrame == null || callerFrame.getClass().getSimpleName().contains(COROUTINE_OWNER_CLASS)) {
      return continuation;
    }
    // In case the caller frame is an instance of ScopeCoroutine, then extract the uCont that is wrapped by the ScopeCoroutine class.
    // ScopeCoroutine is used to wrap the current continuation and pass it into withContext/coroutineScope/flow.. invocation
    Class<?> scopeCoroutine = Class.forName("kotlinx.coroutines.internal.ScopeCoroutine", false, continuation.getClass().getClassLoader());
    if (scopeCoroutine.isInstance(callerFrame)) {
      return getCallerFrame.invoke(callerFrame);
    }
    return callerFrame;
  }

  private static Object getField(Object object, String fieldName) throws ReflectiveOperationException {
    Field field = object.getClass().getField(fieldName);
    field.setAccessible(true);
    return field.get(object);
  }

  private static Object invoke(Object object, String methodName) throws ReflectiveOperationException {
    Method method = object.getClass().getMethod(methodName);
    method.setAccessible(true);
    return method.invoke(object);
  }
}