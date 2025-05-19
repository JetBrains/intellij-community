// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.CommonBundle;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.UIBundle;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XDropFrameHandler;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.VMDisconnectedException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class JvmDropFrameActionHandler implements XDropFrameHandler {

  private static final Logger LOG = Logger.getInstance(JvmDropFrameActionHandler.class);
  private final @NotNull DebuggerSession myDebugSession;

  public JvmDropFrameActionHandler(@NotNull DebuggerSession process) { myDebugSession = process; }

  @Override
  public ThreeState canDropFrame(@NotNull XStackFrame frame) {
    if (frame instanceof JavaStackFrame javaStackFrame) {
      if (javaStackFrame.getStackFrameProxy().getVirtualMachine().canPopFrames()) {
        return javaStackFrame.getDescriptor().canDrop();
      }
    }
    return ThreeState.NO;
  }

  @Override
  public CompletableFuture<Boolean> canDropFrameAsync(@NotNull XStackFrame frame) {
    if (frame instanceof JavaStackFrame javaStackFrame) {
      if (javaStackFrame.getStackFrameProxy().getVirtualMachine().canPopFrames()) {
        return javaStackFrame.getDescriptor().canDropAsync();
      }
    }
    return CompletableFuture.completedFuture(false);
  }

  @Override
  public void drop(@NotNull XStackFrame frame) {
    if (frame instanceof JavaStackFrame stackFrame) {
      var project = myDebugSession.getProject();
      var debugProcess = myDebugSession.getProcess();
      var debuggerContext = myDebugSession.getContextManager().getContext();
      try {
        myDebugSession.setSteppingThrough(stackFrame.getStackFrameProxy().threadProxy());
        if (evaluateFinallyBlocks(project,
                                  XDebuggerBundle.message("xdebugger.reset.frame.title"),
                                  stackFrame,
                                  new XDebuggerEvaluator.XEvaluationCallback() {
                                    @Override
                                    public void evaluated(@NotNull XValue result) {
                                      popFrame(debugProcess, debuggerContext, stackFrame);
                                    }

                                    @Override
                                    public void errorOccurred(final @NotNull String errorMessage) {
                                      showError(project, JavaDebuggerBundle.message("error.executing.finally", errorMessage),
                                                XDebuggerBundle.message("xdebugger.reset.frame.title"));
                                    }
                                  })) {
          return;
        }
        popFrame(debugProcess, debuggerContext, stackFrame);
      }
      catch (InvalidStackFrameException | VMDisconnectedException ignored) {
      }
    }
  }

  public static boolean evaluateFinallyBlocks(Project project,
                                              @Nls String title,
                                              JavaStackFrame stackFrame,
                                              XDebuggerEvaluator.XEvaluationCallback callback) {
    if (!DebuggerSettings.EVALUATE_FINALLY_NEVER.equals(DebuggerSettings.getInstance().EVALUATE_FINALLY_ON_POP_FRAME)) {
      List<PsiStatement> statements = getFinallyStatements(project, stackFrame.getDescriptor().getSourcePosition());
      if (!statements.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (PsiStatement statement : statements) {
          sb.append("\n").append(statement.getText());
        }
        if (DebuggerSettings.EVALUATE_FINALLY_ALWAYS.equals(DebuggerSettings.getInstance().EVALUATE_FINALLY_ON_POP_FRAME)) {
          evaluateAndAct(project, stackFrame, sb, callback);
          return true;
        }
        else {
          int res = MessageDialogBuilder
            .yesNoCancel(title,
                         JavaDebuggerBundle.message("warning.finally.block.detected") + sb)
            .icon(Messages.getWarningIcon())
            .yesText(JavaDebuggerBundle.message("button.execute.finally"))
            .noText(JavaDebuggerBundle.message("button.drop.anyway"))
            .cancelText(CommonBundle.getCancelButtonText())
            .doNotAsk(new DoNotAskOption() {
              @Override
              public boolean isToBeShown() {
                return !DebuggerSettings.EVALUATE_FINALLY_ALWAYS.equals(DebuggerSettings.getInstance().EVALUATE_FINALLY_ON_POP_FRAME) &&
                       !DebuggerSettings.EVALUATE_FINALLY_NEVER.equals(DebuggerSettings.getInstance().EVALUATE_FINALLY_ON_POP_FRAME);
              }

              @Override
              public void setToBeShown(boolean value, int exitCode) {
                if (!value) {
                  DebuggerSettings.getInstance().EVALUATE_FINALLY_ON_POP_FRAME =
                    exitCode == Messages.YES ? DebuggerSettings.EVALUATE_FINALLY_ALWAYS : DebuggerSettings.EVALUATE_FINALLY_NEVER;
                }
                else {
                  DebuggerSettings.getInstance().EVALUATE_FINALLY_ON_POP_FRAME = DebuggerSettings.EVALUATE_FINALLY_ASK;
                }
              }

              @Override
              public boolean canBeHidden() {
                return true;
              }

              @Override
              public boolean shouldSaveOptionsOnCancel() {
                return false;
              }

              @Override
              public @NotNull String getDoNotShowMessage() {
                return UIBundle.message("dialog.options.do.not.show");
              }
            })
            .show(project);

          switch (res) {
            case Messages.CANCEL -> {
              return true;
            }
            case Messages.NO -> {}
            case Messages.YES -> { // evaluate finally
              evaluateAndAct(project, stackFrame, sb, callback);
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static void popFrame(DebugProcessImpl debugProcess, DebuggerContextImpl debuggerContext, JavaStackFrame stackFrame) {
    Objects.requireNonNull(debuggerContext.getManagerThread())
      .schedule(debugProcess.createPopFrameCommand(debuggerContext, stackFrame.getStackFrameProxy()));
  }

  private static void evaluateAndAct(Project project,
                                     JavaStackFrame stackFrame,
                                     StringBuilder sb,
                                     XDebuggerEvaluator.XEvaluationCallback callback) {
    XDebuggerEvaluator evaluator = stackFrame.getEvaluator();
    if (evaluator != null) {
      evaluator.evaluate(XExpressionImpl.fromText(sb.toString(), EvaluationMode.CODE_FRAGMENT),
                         callback,
                         stackFrame.getSourcePosition());
    }
    else {
      Messages.showMessageDialog(project, XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"),
                                 XDebuggerBundle.message("xdebugger.reset.frame.title"),
                                 Messages.getErrorIcon());
    }
  }


  public static void showError(Project project, @NlsContexts.DialogMessage String message, @NlsContexts.DialogTitle String title) {
    ApplicationManager.getApplication().invokeLater(
      () -> Messages.showMessageDialog(project, message, title, Messages.getErrorIcon()),
      ModalityState.any());
  }

  private static List<PsiStatement> getFinallyStatements(Project project, @Nullable SourcePosition position) {
    if (position == null) {
      return Collections.emptyList();
    }
    List<PsiStatement> res = new ArrayList<>();
    PsiElement element = position.getFile().findElementAt(position.getOffset());
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
    while (tryStatement != null) {
      PsiResourceList resourceList = tryStatement.getResourceList();
      if (resourceList != null) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        for (PsiResourceListElement listElement : resourceList) {
          String varName = getResourceName(listElement);
          if (varName != null) {
            res.add(factory.createStatementFromText("if (" + varName + " != null) " + varName + ".close();", tryStatement));
          }
        }
      }
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock != null) {
        ContainerUtil.addAll(res, finallyBlock.getStatements());
      }
      tryStatement = PsiTreeUtil.getParentOfType(tryStatement, PsiTryStatement.class);
    }
    return res;
  }

  private static String getResourceName(PsiResourceListElement resource) {
    if (resource instanceof PsiResourceVariable) {
      return ((PsiResourceVariable)resource).getName();
    }
    else if (resource instanceof PsiResourceExpression) {
      return ((PsiResourceExpression)resource).getExpression().getText();
    }
    LOG.error("Unknown PsiResourceListElement type: " + resource.getClass());
    return null;
  }
}
