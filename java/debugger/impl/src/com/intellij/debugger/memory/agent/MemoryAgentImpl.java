// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class MemoryAgentImpl implements MemoryAgent {
  static final MemoryAgent DISABLED = new MemoryAgentImpl(MemoryAgentCapabilities.DISABLED);
  private final MemoryAgentCapabilities myCapabilities;

  MemoryAgentImpl(@NotNull MemoryAgentCapabilities capabilities) {
    myCapabilities = capabilities;
  }

  @NotNull
  @Override
  public MemoryAgentActionResult<Pair<long[], ObjectReference[]>> estimateObjectSize(@NotNull EvaluationContextImpl evaluationContext,
                                                                                   @NotNull ObjectReference reference,
                                                                                   long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canEstimateObjectSize()) {
      throw new UnsupportedOperationException("Memory agent can't estimate object size");
    }

    return MemoryAgentOperations.estimateObjectSize(evaluationContext, reference, timeoutInMillis);
  }

  @NotNull
  @Override
  public MemoryAgentActionResult<long[]> estimateObjectsSizes(@NotNull EvaluationContextImpl evaluationContext,
                                                              @NotNull List<ObjectReference> references,
                                                              long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canEstimateObjectsSizes()) {
      throw new UnsupportedOperationException("Memory agent can't estimate objects sizes");
    }

    return MemoryAgentOperations.estimateObjectsSizes(evaluationContext, references, timeoutInMillis);
  }

  @NotNull
  @Override
  public MemoryAgentActionResult<long[]> getShallowSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                 @NotNull List<ReferenceType> classes,
                                                                 long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canGetShallowSizeByClasses()) {
      throw new UnsupportedOperationException("Memory agent can't get shallow size by classes");
    }

    return MemoryAgentOperations.getShallowSizeByClasses(evaluationContext, classes, timeoutInMillis);
  }

  @NotNull
  @Override
  public MemoryAgentActionResult<long[]> getRetainedSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                  @NotNull List<ReferenceType> classes,
                                                                  long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canGetRetainedSizeByClasses()) {
      throw new UnsupportedOperationException("Memory agent can't get retained size by classes");
    }

    return MemoryAgentOperations.getRetainedSizeByClasses(evaluationContext, classes, timeoutInMillis);
  }

  @NotNull
  @Override
  public MemoryAgentActionResult<Pair<long[], long[]>> getShallowAndRetainedSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                                          @NotNull List<ReferenceType> classes,
                                                                                          long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canGetRetainedSizeByClasses() || !myCapabilities.canGetShallowSizeByClasses()) {
      throw new UnsupportedOperationException("Memory agent can't get shallow and retained size by classes");
    }

    return MemoryAgentOperations.getShallowAndRetainedSizeByClasses(evaluationContext, classes, timeoutInMillis);
  }

  @NotNull
  @Override
  public MemoryAgentActionResult<ReferringObjectsInfo> findPathsToClosestGCRoots(@NotNull EvaluationContextImpl evaluationContext,
                                                                                 @NotNull ObjectReference reference,
                                                                                 int pathsNumber,
                                                                                 int objectsNumber,
                                                                                 long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canFindPathsToClosestGcRoots()) {
      throw new UnsupportedOperationException("Memory agent can't provide paths to closest gc roots");
    }

    return MemoryAgentOperations.findPathsToClosestGCRoots(evaluationContext, reference, pathsNumber, objectsNumber, timeoutInMillis);
  }

  @NotNull
  @Override
  public MemoryAgentCapabilities capabilities() {
    return myCapabilities;
  }
}
