// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.memory.agent.MemoryAgent;
import com.intellij.debugger.memory.agent.MemoryAgentCapabilities;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.messages.MessageDialog;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public class CalculateRetainedSizeAction extends MemoryAgentActionBase {
  @Override
  protected void perform(@NotNull EvaluationContextImpl evaluationContext,
                         @NotNull ObjectReference reference,
                         @NotNull XValueNodeImpl node) throws EvaluateException {
    MemoryAgent memoryAgent = MemoryAgent.get(evaluationContext.getDebugProcess());
    long size = memoryAgent.estimateObjectSize(evaluationContext, reference);
    ApplicationManager.getApplication().invokeLater(
      () -> new MessageDialog(node.getTree().getProject(), String.valueOf(size), "Size of the Object",
                              ArrayUtilRt.EMPTY_STRING_ARRAY, 0, null, false)
        .show());
  }

  @Override
  protected boolean isEnabled(@NotNull MemoryAgentCapabilities agentCapabilities) {
    return agentCapabilities.canEstimateObjectSize();
  }
}
