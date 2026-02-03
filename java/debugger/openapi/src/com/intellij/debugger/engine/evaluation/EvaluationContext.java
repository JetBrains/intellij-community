// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.SuspendContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface EvaluationContext extends StackFrameContext {
  @Override
  @NotNull
  DebugProcess getDebugProcess();

  EvaluationContext createEvaluationContext(Value value);

  @NotNull
  SuspendContext getSuspendContext();

  Project getProject();

  @Nullable
  ClassLoaderReference getClassLoader() throws EvaluateException;

  @Nullable
  Value computeThisObject() throws EvaluateException;

  void keep(Value value);

  <T extends Value> T computeAndKeep(@NotNull ThrowableComputable<T, EvaluateException> computable) throws EvaluateException;
}
