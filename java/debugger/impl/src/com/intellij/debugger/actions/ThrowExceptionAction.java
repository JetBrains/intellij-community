/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
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
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.evaluate.XExpressionDialog;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ThrowExceptionAction extends DebuggerAction {
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    final JavaStackFrame stackFrame = PopFrameAction.getStackFrame(e);
    if (stackFrame == null || project == null) {
      return;
    }
    final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if (debugProcess == null) {
      return;
    }

    final StackFrameProxyImpl proxy = stackFrame.getStackFrameProxy();
    final ThreadReferenceProxyImpl thread = proxy.threadProxy();

    debugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext, thread) {
      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        ApplicationManager.getApplication().invokeLater(
          () -> new XExpressionDialog(project, debugProcess.getXdebugProcess().getEditorsProvider(), "throwExceptionValue",
                                    "Exception To Throw", stackFrame.getSourcePosition(), null) {
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
          showError(debugProcess.getProject(), DebuggerBundle.message("error.throw.exception", e.getLocalizedMessage()));
          return;
        }
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          if (dialog != null) {
            dialog.close(DialogWrapper.OK_EXIT_CODE);
          }
          debugProcess.getSession().stepInto(true, null);
        });
      }
    });
  }

  private static void showError(Project project, String message) {
    PopFrameAction.showError(project, message, UIUtil.removeMnemonic(ActionsBundle.actionText("Debugger.ThrowException")));
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
                             showError(project, DebuggerBundle.message("error.unable.to.evaluate.expression") + ": " + errorMessage);
                           }
                         }, stackFrame.getSourcePosition());
    }
    else {
      showError(project, XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"));
    }
  }

  public void update(@NotNull AnActionEvent e) {
    JavaStackFrame stackFrame = PopFrameAction.getStackFrame(e);
    boolean enable = stackFrame != null && stackFrame.getDescriptor().getUiIndex() == 0;

    if (ActionPlaces.isMainMenuOrActionSearch(e.getPlace()) || ActionPlaces.DEBUGGER_TOOLBAR.equals(e.getPlace())) {
      e.getPresentation().setEnabled(enable);
    }
    else {
      e.getPresentation().setVisible(enable);
    }
  }
}
