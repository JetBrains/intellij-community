// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.managerThread.DebuggerCommand;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

public abstract class NativeAgentActionBase extends DebuggerTreeAction {
  protected final Logger LOG = Logger.getInstance(this.getClass());

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    Project project = node.getTree().getProject();
    XValue container = node.getValueContainer();
    XDebugSession currentSession = XDebuggerManager.getInstance(project).getCurrentSession();
    XSuspendContext suspendContext = currentSession != null ? currentSession.getSuspendContext() : null;
    DebugProcessImpl debugProcess =
      suspendContext instanceof SuspendContextImpl ? ((SuspendContextImpl)suspendContext).getDebugProcess() : null;
    if (debugProcess == null) return;
    debugProcess.getManagerThread().invokeCommand(new DebuggerCommand() {
      @Override
      public void action() {
        if (container instanceof JavaValue) {
          EvaluationContextImpl evaluationContext = debugProcess.getDebuggerContext().createEvaluationContext();
          if (evaluationContext == null) return;
          Value value = ((JavaValue)container).getDescriptor().getValue();
          perform(evaluationContext, (ObjectReference)value, node);
        }
      }

      @Override
      public void commandCancelled() {
        LOG.info("command cancelled");
      }
    });
  }

  @Override
  protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    if (!super.isEnabled(node, e)) return false;
    XValue container = node.getValueContainer();
    if (container instanceof JavaValue) {
      if (((JavaValue)container).getDescriptor().getValue() instanceof ObjectReference) return true;
    }

    return false;
  }

  protected abstract void perform(@NotNull EvaluationContextImpl evaluationContext,
                                  @NotNull ObjectReference reference,
                                  @NotNull XValueNodeImpl node);
}
