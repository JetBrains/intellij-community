// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.requests;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface RequestManager {
  void callbackOnPrepareClasses(ClassPrepareRequestor requestor, String classOrPatternToBeLoaded);

  void callbackOnPrepareClasses(ClassPrepareRequestor requestor, SourcePosition classPosition) throws EvaluateException;

  @Nullable
  ClassPrepareRequest createClassPrepareRequest(ClassPrepareRequestor requestor, String pattern);

  void enableRequest(EventRequest request);

  void setInvalid(Requestor requestor, String message);
}
