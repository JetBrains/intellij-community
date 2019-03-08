// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.ReferringObject;
import com.intellij.debugger.engine.ReferringObjectsProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryAgentReferringObjectsProvider implements ReferringObjectsProvider {
  private final MemoryAgent myAgent;
  private final int myObjectsToRequestLimit;
  private final Map<ObjectReference, ReferringObjectsInfo> myCachedRequests = new HashMap<>();

  public MemoryAgentReferringObjectsProvider(@NotNull MemoryAgent agent, int limit) {
    myAgent = agent;
    myObjectsToRequestLimit = limit;
  }

  @NotNull
  @Override
  public List<ReferringObject> getReferringObjects(@NotNull ObjectReference value, long limit) throws EvaluateException {
    if (myCachedRequests.containsKey(value)) {
      return myCachedRequests.get(value).getReferringObjects(value, limit);
    }

    for (ReferringObjectsInfo provider : myCachedRequests.values()) {
      if (provider.hasReferringObjectsFor(value)) {
        return provider.getReferringObjects(value, limit);
      }
    }

    ReferringObjectsInfo roots = myAgent.findGcRoots(value, myObjectsToRequestLimit);
    myCachedRequests.put(value, roots);
    return roots.getReferringObjects(value, limit);
  }
}
