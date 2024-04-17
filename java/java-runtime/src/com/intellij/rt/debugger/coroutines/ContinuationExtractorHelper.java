// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger.coroutines;


public final class ContinuationExtractorHelper {
  private static final String GET_CALLER_FRAME_METHOD = "getCallerFrame";

  public static Object getRootContinuation(Object continuation) throws ReflectiveOperationException {
    Object parentFrame = invoke(continuation, GET_CALLER_FRAME_METHOD);
    return (parentFrame != null) ? getRootContinuation(parentFrame) : continuation;
  }

  private static Object invoke(Object object, String methodName) throws ReflectiveOperationException {
    return object.getClass().getMethod(methodName).invoke(object);
  }
}