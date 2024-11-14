// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.debugger.actions;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.evaluate.XExpressionDialog;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class ThrowExceptionAction extends DebuggerAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    final JavaStackFrame stackFrame = getStackFrame(e);
    if (stackFrame == null || project == null) {
      return;
    }
    final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if (debugProcess == null) {
      return;
    }

    final StackFrameProxyImpl proxy = stackFrame.getStackFrameProxy();
    final ThreadReferenceProxyImpl thread = proxy.threadProxy();

    Objects.requireNonNull(debuggerContext.getManagerThread()).schedule(new DebuggerContextCommandImpl(debuggerContext, thread) {
      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        ApplicationManager.getApplication().invokeLater(
          () -> new XExpressionDialog(project, debugProcess.getXdebugProcess().getEditorsProvider(), "throwExceptionValue",
                                      JavaDebuggerBundle.message("dialog.title.exception.to.throw"), stackFrame.getSourcePosition(), null) {
            @Override
            protected void doOKAction() {
              evaluateAndReturn(project, stackFrame, debugProcess, getExpression(), this);
            }
          }.show());
      }
    });
  }

  private static void throwException(final Value value,
                                     final ThreadReferenceProxyImpl thread,
                                     final DebugProcessImpl debugProcess,
                                     @Nullable final DialogWrapper dialog) {
    debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() {
        try {
          thread.stop((ObjectReference)value);
        }
        catch (Exception e) {
          showError(debugProcess.getProject(), JavaDebuggerBundle.message("error.throw.exception", e.getLocalizedMessage()));
          return;
        }
        SwingUtilities.invokeLater(() -> {
          if (dialog != null) {
            dialog.close(DialogWrapper.OK_EXIT_CODE);
          }
          debugProcess.getSession().stepInto(true, null);
        });
      }
    });
  }

  private static void showError(Project project, @NlsContexts.DialogMessage String message) {
    JvmDropFrameActionHandler.showError(project, message, UIUtil.removeMnemonic(ActionsBundle.actionText("Debugger.ThrowException")));
  }

  private static void evaluateAndReturn(final Project project,
                                        final JavaStackFrame stackFrame,
                                        final DebugProcessImpl debugProcess,
                                        XExpression expression,
                                        final DialogWrapper dialog) {
    XDebuggerEvaluator evaluator = stackFrame.getEvaluator();
    if (evaluator != null) {
      evaluator.evaluate(expression,
                         new XDebuggerEvaluator.XEvaluationCallback() {
                           @Override
                           public void evaluated(@NotNull XValue result) {
                             if (result instanceof JavaValue) {
                               throwException(((JavaValue)result).getDescriptor().getValue(),
                                              stackFrame.getDescriptor().getFrameProxy().threadProxy(), debugProcess, dialog);
                             }
                           }

                           @Override
                           public void errorOccurred(@NotNull final String errorMessage) {
                             showError(project, JavaDebuggerBundle.message("error.unable.to.evaluate.expression") + ": " + errorMessage);
                           }
                         }, stackFrame.getSourcePosition());
    }
    else {
      showError(project, XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    JavaStackFrame stackFrame = getStackFrame(e);
    DebuggerUIUtil.setActionEnabled(e, stackFrame != null && stackFrame.getDescriptor().getUiIndex() == 0);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
