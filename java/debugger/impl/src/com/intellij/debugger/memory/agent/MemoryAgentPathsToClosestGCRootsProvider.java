// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.ReferringObject;
import com.intellij.debugger.engine.ReferringObjectsProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.util.registry.Registry;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryAgentPathsToClosestGCRootsProvider implements ReferringObjectsProvider {
  private final Map<ObjectReference, ReferringObjectsInfo> myCachedRequests = new HashMap<>();
  private final int myPathsToRequestLimit;
  private final int myObjectsToRequestLimit;
  private final ReferringObjectsProvider myDefaultProvider;

  public MemoryAgentPathsToClosestGCRootsProvider(int pathsToRequestLimit,
                                                  int objectsToRequestLimit,
                                                  ReferringObjectsProvider defaultProvider) {
    myPathsToRequestLimit = pathsToRequestLimit;
    myObjectsToRequestLimit = objectsToRequestLimit;
    myDefaultProvider = defaultProvider;
  }

  @Override
  public @NotNull @Unmodifiable List<ReferringObject> getReferringObjects(@NotNull EvaluationContextImpl evaluationContext,
                                                                          @NotNull ObjectReference value,
                                                                          long limit) throws EvaluateException {
    if (myCachedRequests.containsKey(value)) {
      return myCachedRequests.get(value).getReferringObjects(value, limit);
    }

    for (ReferringObjectsInfo provider : myCachedRequests.values()) {
      if (provider.hasReferringObjectsFor(value)) {
        return provider.getReferringObjects(value, limit);
      }
    }

    MemoryAgent memoryAgent = MemoryAgent.get(evaluationContext);
    if (memoryAgent.isDisabled()) {
      return myDefaultProvider.getReferringObjects(evaluationContext, value, myObjectsToRequestLimit);
    }

    ReferringObjectsInfo roots = getPathsToGcRoots(memoryAgent, evaluationContext, value);
    myCachedRequests.put(value, roots);
    return roots.getReferringObjects(value, limit);
  }

  private ReferringObjectsInfo getPathsToGcRoots(@NotNull MemoryAgent memoryAgent,
                                                 @NotNull EvaluationContextImpl evaluationContext,
                                                 @NotNull ObjectReference value) throws EvaluateException {
    return memoryAgent.findPathsToClosestGCRoots(
      evaluationContext,
      value,
      myPathsToRequestLimit,
      myObjectsToRequestLimit,
      Registry.get("debugger.memory.agent.action.timeout").asInteger()
    ).getResult();
  }
}
