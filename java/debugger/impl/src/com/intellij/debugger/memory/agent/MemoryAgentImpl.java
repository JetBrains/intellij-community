// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class MemoryAgentImpl implements MemoryAgent {
  private final EvaluationContextImpl myEvaluationContext;

  MemoryAgentImpl(@NotNull EvaluationContextImpl evaluationContext) {
    myEvaluationContext = evaluationContext;
  }

  @Override
  public long estimateObjectSize(@NotNull ObjectReference reference) throws EvaluateException {
    return MemoryAgentOperations.estimateObjectSize(myEvaluationContext, reference);
  }

  @Override
  public long[] estimateObjectsSizes(@NotNull List<ObjectReference> references) throws EvaluateException {
    return MemoryAgentOperations.estimateObjectsSizes(myEvaluationContext, references);
  }

  @NotNull
  @Override
  public ReferringObjectsInfo findReferringObjects(@NotNull ObjectReference reference, int limit) throws EvaluateException {
    return MemoryAgentOperations.findReferringObjects(myEvaluationContext, reference, limit);
  }

  @NotNull
  @Override
  public MemoryAgentCapabilities capabilities() {
    return MemoryAgent.capabilities(myEvaluationContext.getDebugProcess());
  }
}
