// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.memory.agent.MemoryAgent;
import com.intellij.debugger.memory.agent.MemoryAgentActionResult;
import com.intellij.debugger.memory.agent.ui.RetainedSizeDialog;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@ApiStatus.Internal
public final class CalculateRetainedSizeActionUtil {
  private static final Logger LOG = Logger.getInstance(CalculateRetainedSizeActionUtil.class);

  public static void showDialog(@NotNull XValue xValue, @NotNull String nodeName, DebugProcessImpl debugProcess) {
    ObjectReference reference = DebuggerTreeAction.getObjectReference(xValue);
    if (debugProcess == null || reference == null) return;

    XDebugSession session = debugProcess.getSession().getXDebugSession();
    if (session == null) return;
    RetainedSizeDialog dialog = new RetainedSizeDialog(session.getProject(), session.getDebugProcess().getEditorsProvider(),
                                                       session.getCurrentPosition(),
                                                       nodeName, xValue, ((XDebugSessionImpl)session).getValueMarkers(),
                                                       session,
                                                       true);
    dialog.show();

    SuspendContextImpl suspendContext = debugProcess.getSuspendManager().getPausedContext();
    suspendContext.getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        try {
          if (dialog.isDisposed()) {
            return;
          }

          EvaluationContextImpl evaluationContext = new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy());
          MemoryAgent memoryAgent = MemoryAgent.get(evaluationContext);
          if (memoryAgent.isDisabled()) {
            dialog.setAgentCouldntBeLoadedMessage();
            return;
          }

          Disposer.register(dialog.getDisposable(), () -> memoryAgent.cancelAction());
          memoryAgent.setProgressIndicator(dialog.createProgressIndicator());
          MemoryAgentActionResult<Pair<long[], ObjectReference[]>> result = memoryAgent.estimateObjectSize(
            evaluationContext, reference, Registry.get("debugger.memory.agent.action.timeout").asInteger()
          );

          if (dialog.isDisposed()) {
            return;
          }

          interpretResult(result, dialog);
        }
        catch (EvaluateException e) {
          XDebuggerManagerImpl.getNotificationGroup().createNotification(JavaDebuggerBundle.message("action.failed"), NotificationType.ERROR);
        }
      }

      private static void interpretResult(@NotNull MemoryAgentActionResult<Pair<long[], ObjectReference[]>> result,
                                          @NotNull RetainedSizeDialog dialog) {
        if (result.executedSuccessfully()) {
          Pair<long[], ObjectReference[]> sizesAndHeldObjects = result.getResult();
          long[] sizes = sizesAndHeldObjects.getFirst();
          dialog.setHeldObjectsAndSizes(
            Arrays.asList(sizesAndHeldObjects.getSecond()),
            sizes[0],
            sizes[1]
          );
        }
        else {
          dialog.setCalculationTimeoutMessage();
        }
      }

      @Override
      public void commandCancelled() {
        LOG.info("command cancelled");
      }
    });
  }
}
