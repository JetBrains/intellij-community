// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.ReferringObject;
import com.intellij.debugger.engine.ReferringObjectsProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class MemoryAgentReferringObjectsProviderBase implements ReferringObjectsProvider {
  private final Map<ObjectReference, ReferringObjectsInfo> myCachedRequests = new HashMap<>();

  @NotNull
  @Override
  public List<ReferringObject> getReferringObjects(@NotNull EvaluationContextImpl evaluationContext,
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

    ReferringObjectsInfo roots = getPathsToGcRoots(evaluationContext, value);
    myCachedRequests.put(value, roots);
    return roots.getReferringObjects(value, limit);
  }

  protected abstract ReferringObjectsInfo getPathsToGcRoots(@NotNull EvaluationContextImpl evaluationContext,
                                                            @NotNull ObjectReference value) throws EvaluateException;
}
