// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    return MemoryAgentInitializer.getAgent(debugProcess);
  }

  void cancelAction();

  @Nullable
  MemoryAgentProgressPoint checkProgress();

  void setProgressIndicator(@NotNull ProgressIndicator progressIndicator);

  @NotNull
  MemoryAgentCapabilities getCapabilities();

  @NotNull
  MemoryAgentActionResult<Pair<long[], ObjectReference[]>> estimateObjectSize(@NotNull EvaluationContextImpl evaluationContext,
                                                                              @NotNull ObjectReference reference,
                                                                              long timeoutInMillis) throws EvaluateException;

  @NotNull
  MemoryAgentActionResult<long[]> estimateObjectsSizes(@NotNull EvaluationContextImpl evaluationContext,
                                                       @NotNull List<ObjectReference> references,
                                                       long timeoutInMillis) throws EvaluateException;

  @NotNull
  MemoryAgentActionResult<long[]> getShallowSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                          @NotNull List<ReferenceType> classes,
                                                          long timeoutInMillis) throws EvaluateException;

  @NotNull
  MemoryAgentActionResult<long[]> getRetainedSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                           @NotNull List<ReferenceType> classes,
                                                           long timeoutInMillis) throws EvaluateException;

  @NotNull
  MemoryAgentActionResult<Pair<long[], long[]>> getShallowAndRetainedSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                                   @NotNull List<ReferenceType> classes,
                                                                                   long timeoutInMillis) throws EvaluateException;

  @NotNull
  MemoryAgentActionResult<ReferringObjectsInfo> findPathsToClosestGCRoots(@NotNull EvaluationContextImpl evaluationContext,
                                                                          @NotNull ObjectReference reference,
                                                                          int pathsNumber, int objectsNumber,
                                                                          long timeoutInMillis) throws EvaluateException;
}
