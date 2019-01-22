// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.memory.agent.MemoryAgent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.messages.MessageDialog;
import com.intellij.util.ArrayUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public class CalculateRetainedSizeAction extends NativeAgentActionBase {
  @Override
  protected void perform(@NotNull MemoryAgent memoryAgent,
                         @NotNull ObjectReference reference,
                         @NotNull XValueNodeImpl node) throws EvaluateException {
    long size = memoryAgent.evaluateObjectSize(reference);
    ApplicationManager.getApplication().invokeLater(
      () -> new MessageDialog(node.getTree().getProject(), String.valueOf(size), "Size of the Object",
                              ArrayUtil.EMPTY_STRING_ARRAY, 0, null, false)
        .show());
  }

  @Override
  protected boolean isEnabled(@NotNull MemoryAgent agent) {
    return agent.canEvaluateObjectSize();
  }
}
