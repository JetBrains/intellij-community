// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public class MemoryAgentPathsToClosestGCRootsProvider extends MemoryAgentReferringObjectsProviderBase {
  private final int myPathsToRequestLimit;

  public MemoryAgentPathsToClosestGCRootsProvider(int pathsToRequestLimit) {
    myPathsToRequestLimit = pathsToRequestLimit;
  }

  @Override
  protected ReferringObjectsInfo getPathsToGcRoots(@NotNull EvaluationContextImpl evaluationContext,
                                                   @NotNull ObjectReference value) throws EvaluateException {
    MemoryAgent memoryAgent = MemoryAgent.get(evaluationContext.getDebugProcess());
    if (!memoryAgent.capabilities().canFindPathsToClosestGcRoots()) {
      throw new UnsupportedOperationException();
    }

    return memoryAgent.findPathsToClosestGCRoots(evaluationContext, value, myPathsToRequestLimit);
  }
}
