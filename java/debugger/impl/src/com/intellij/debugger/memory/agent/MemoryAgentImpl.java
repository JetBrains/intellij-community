// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.util.Pair;
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

  @Override
  public Pair<Long, ObjectReference[]> estimateObjectSize(@NotNull EvaluationContextImpl evaluationContext, @NotNull ObjectReference reference)
    throws EvaluateException {
    if (!myCapabilities.canEstimateObjectSize()) {
      throw new UnsupportedOperationException("Memory agent can't estimate object size");
    }
    return MemoryAgentOperations.estimateObjectSize(evaluationContext, reference);
  }

  @Override
  public long[] estimateObjectsSizes(@NotNull EvaluationContextImpl evaluationContext, @NotNull List<ObjectReference> references)
    throws EvaluateException {
    if (!myCapabilities.canEstimateObjectsSizes()) {
      throw new UnsupportedOperationException("Memory agent can't estimate objects sizes");
    }

    return MemoryAgentOperations.estimateObjectsSizes(evaluationContext, references);
  }

  @Override
  public long[] getShallowSizeByClasses(@NotNull EvaluationContextImpl evaluationContext, @NotNull List<ReferenceType> classes)
    throws EvaluateException {
    if (!myCapabilities.canGetShallowSizeByClasses()) {
      throw new UnsupportedOperationException("Memory agent can't get shallow size by classes");
    }

    return MemoryAgentOperations.getShallowSizeByClasses(evaluationContext, classes);
  }

  @Override
  public long[] getRetainedSizeByClasses(@NotNull EvaluationContextImpl evaluationContext, @NotNull List<ReferenceType> classes)
    throws EvaluateException {
    if (!myCapabilities.canGetRetainedSizeByClasses()) {
      throw new UnsupportedOperationException("Memory agent can't get retained size by classes");
    }

    return MemoryAgentOperations.getRetainedSizeByClasses(evaluationContext, classes);
  }

  @Override
  public Pair<long[], long[]> getShallowAndRetainedSizeByClasses(@NotNull EvaluationContextImpl evaluationContext, @NotNull List<ReferenceType> classes)
    throws EvaluateException {
    if (!myCapabilities.canGetRetainedSizeByClasses() || !myCapabilities.canGetShallowSizeByClasses()) {
      throw new UnsupportedOperationException("Memory agent can't get shallow and retained size by classes");
    }

    return MemoryAgentOperations.getShallowAndRetainedSizeByClasses(evaluationContext, classes);
  }

  @Override
  public @NotNull ReferringObjectsInfo findPathsToClosestGCRoots(@NotNull EvaluationContextImpl evaluationContext,
                                                                 @NotNull ObjectReference reference,
                                                                 int pathsNumber, int objectsNumber) throws EvaluateException {
    if (!myCapabilities.canFindPathsToClosestGcRoots()) {
      throw new UnsupportedOperationException("Memory agent can't provide paths to closest gc roots");
    }

    return MemoryAgentOperations.findPathsToClosestGCRoots(evaluationContext, reference, pathsNumber, objectsNumber);
  }

  @NotNull
  @Override
  public MemoryAgentCapabilities capabilities() {
    return myCapabilities;
  }
}
