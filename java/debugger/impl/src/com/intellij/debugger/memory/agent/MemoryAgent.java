// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface MemoryAgent {
  /**
   * Maximal number of paths that will be retrieved by {@code findPathsToClosestGCRoots} call
   */
  int DEFAULT_GC_ROOTS_PATHS_LIMIT = 10;

  /**
   * Maximal number of objects that will be retrieved by {@code findPathsToClosestGCRoots} call
   */
  int DEFAULT_GC_ROOTS_OBJECTS_LIMIT = 50;

  @NotNull
  static MemoryAgent get(@NotNull DebugProcessImpl debugProcess) {
    if (!DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT) return MemoryAgentImpl.DISABLED;

    return MemoryAgentOperations.getAgent(debugProcess);
  }

  @NotNull
  MemoryAgentCapabilities capabilities();

  long estimateObjectSize(@NotNull EvaluationContextImpl evaluationContext, @NotNull ObjectReference reference) throws EvaluateException;

  long[] estimateObjectsSizes(@NotNull EvaluationContextImpl evaluationContext, @NotNull List<ObjectReference> references)
    throws EvaluateException;

  @NotNull
  ReferringObjectsInfo findPathsToClosestGCRoots(@NotNull EvaluationContextImpl evaluationContext, @NotNull ObjectReference reference,
                                                 int pathsNumber, int objectsNumber)
    throws EvaluateException;
}
