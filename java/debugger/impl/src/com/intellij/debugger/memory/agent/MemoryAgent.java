// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface MemoryAgent {
  /**
   * Maximal number of objects that will be retrieved by {@code findReferringObjects} call
   */
  int DEFAULT_GC_ROOTS_OBJECTS_LIMIT = 1000;

  @NotNull
  static MemoryAgentCapabilities capabilities(@NotNull DebugProcessImpl debugProcess) {
    return MemoryAgentCapabilities.get(debugProcess);
  }

  @NotNull
  static MemoryAgent using(@NotNull EvaluationContextImpl evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return new MemoryAgentImpl(evaluationContext);
  }

  @Nullable
  static MemoryAgent using(@NotNull DebugProcessImpl debugProcess) {
    EvaluationContextImpl context = debugProcess.getDebuggerContext().createEvaluationContext();
    return context != null ? using(context) : null;
  }

  @NotNull
  MemoryAgentCapabilities capabilities();

  long estimateObjectSize(@NotNull ObjectReference reference) throws EvaluateException;

  long[] estimateObjectsSizes(@NotNull List<ObjectReference> references) throws EvaluateException;

  @NotNull
  ReferringObjectsInfo findReferringObjects(@NotNull ObjectReference reference, int limit) throws EvaluateException;
}
