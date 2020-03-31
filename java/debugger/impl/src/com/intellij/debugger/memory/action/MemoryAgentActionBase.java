// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.memory.agent.MemoryAgent;
import com.intellij.debugger.memory.agent.MemoryAgentCapabilities;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public abstract class MemoryAgentActionBase extends DebuggerTreeAction {
  protected final Logger LOG = Logger.getInstance(this.getClass());

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    Project project = node.getTree().getProject();
    DebugProcessImpl debugProcess = JavaDebugProcess.getCurrentDebugProcess(project);
    ObjectReference reference = getObjectReference(node);
    if (debugProcess == null || reference == null) return;
    SuspendContextImpl suspendContext = debugProcess.getSuspendManager().getPausedContext();
    debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        EvaluationContextImpl evaluationContext = new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy());
        try {
          perform(evaluationContext, reference, node);
        }
        catch (EvaluateException ex) {
          XDebuggerManagerImpl.NOTIFICATION_GROUP.createNotification(JavaDebuggerBundle.message("action.failed"), NotificationType.ERROR);
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
    DebugProcessImpl debugProcess = JavaDebugProcess.getCurrentDebugProcess(node.getTree().getProject());
    if (debugProcess == null || !debugProcess.isEvaluationPossible() || !MemoryAgent.get(debugProcess).capabilities().isLoaded()) {
      e.getPresentation().setVisible(false);
      return false;
    }

    ObjectReference reference = getObjectReference(node);

    return reference != null && isEnabled(MemoryAgent.get(debugProcess).capabilities());
  }

  protected abstract boolean isEnabled(@NotNull MemoryAgentCapabilities agentCapabilities);

  protected abstract void perform(@NotNull EvaluationContextImpl evaluationContext,
                                  @NotNull ObjectReference reference,
                                  @NotNull XValueNodeImpl node) throws EvaluateException;
}
