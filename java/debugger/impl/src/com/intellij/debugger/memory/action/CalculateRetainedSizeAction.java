// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.memory.agent.AgentLoader;
import com.intellij.debugger.memory.agent.MemoryAgent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.messages.MessageDialog;
import com.intellij.util.ArrayUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class CalculateRetainedSizeAction extends NativeAgentActionBase {
  @Override
  protected void perform(@NotNull EvaluationContextImpl evaluationContext,
                         @NotNull ObjectReference reference,
                         @NotNull XValueNodeImpl node) {
    MemoryAgent memoryAgent = new AgentLoader().load(evaluationContext, evaluationContext.getDebugProcess().getVirtualMachineProxy());
    long size = memoryAgent.evaluateObjectSize(reference);
    ApplicationManager.getApplication().invokeLater(
      () -> new MessageDialog(node.getTree().getProject(), String.valueOf(size), "Size of the Object",
                              ArrayUtil.EMPTY_STRING_ARRAY, 0, null, false)
        .show());
  }
}
