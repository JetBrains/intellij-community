// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.memory.agent.MemoryAgent;
import com.intellij.debugger.memory.agent.MemoryAgentActionResult;
import com.intellij.debugger.memory.agent.ui.RetainedSizeDialog;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class CalculateRetainedSizeAction extends DebuggerTreeAction {
  protected static final Logger LOG = Logger.getInstance(CalculateRetainedSizeAction.class);

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    DebugProcessImpl debugProcess = JavaDebugProcess.getCurrentDebugProcess(node.getTree().getProject());
    ObjectReference reference = getObjectReference(node);
    if (debugProcess == null || reference == null) return;

    XDebuggerTree tree = node.getTree();
    RetainedSizeDialog dialog = new RetainedSizeDialog(tree.getProject(), tree.getEditorsProvider(), tree.getSourcePosition(),
                                                       nodeName, node.getValueContainer(), tree.getValueMarkers(),
                                                       XDebuggerManager.getInstance(tree.getProject()).getCurrentSession(),
                                                       true);
    dialog.show();

    SuspendContextImpl suspendContext = debugProcess.getSuspendManager().getPausedContext();
    debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        try {
          if (dialog.isDisposed()) {
            return;
          }

          EvaluationContextImpl evaluationContext = new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy());
          MemoryAgent memoryAgent = MemoryAgent.get(debugProcess);
          Disposer.register(dialog.getDisposable(), () -> memoryAgent.cancelAction());
          memoryAgent.setProgressIndicator(dialog.createProgressIndicator());
          MemoryAgentActionResult<Pair<long[], ObjectReference[]>> result = memoryAgent.estimateObjectSize(
            evaluationContext, reference, Registry.get("debugger.memory.agent.action.timeout").asInteger()
          );

          if (dialog.isDisposed()) {
            return;
          }

          if (result.executedSuccessfully()) {
            Pair<long[], ObjectReference[]> sizesAndHeldObjects = result.getResult();
            dialog.setHeldObjectsAndSizes(
              Arrays.asList(sizesAndHeldObjects.getSecond()),
              sizesAndHeldObjects.getFirst()[0],
              sizesAndHeldObjects.getFirst()[1]
            );
          } else {
            dialog.setCalculationTimeout();
          }
        }
        catch (EvaluateException e) {
          XDebuggerManagerImpl.getNotificationGroup().createNotification(JavaDebuggerBundle.message("action.failed"), NotificationType.ERROR);
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
    if (debugProcess == null || !debugProcess.isEvaluationPossible() || !MemoryAgent.get(debugProcess).getCapabilities().isLoaded()) {
      e.getPresentation().setVisible(false);
      return false;
    }

    ObjectReference reference = getObjectReference(node);
    return reference != null && MemoryAgent.get(debugProcess).getCapabilities().canEstimateObjectSize();
  }
}
