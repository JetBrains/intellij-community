/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.actions;

import com.intellij.CommonBundle;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.NativeMethodException;
import com.sun.jdi.VMDisconnectedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PopFrameAction extends DebuggerAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(PopFrameAction.class);

  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final JavaStackFrame stackFrame = getStackFrame(e);
    if (stackFrame == null || stackFrame.getStackFrameProxy().isBottom()) {
      return;
    }
    try {
      final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
      final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
      if(debugProcess == null) {
        return;
      }

      debugProcess.getSession().setSteppingThrough(stackFrame.getStackFrameProxy().threadProxy());
      if (evaluateFinallyBlocks(project,
                                UIUtil.removeMnemonic(ActionsBundle.actionText(DebuggerActions.POP_FRAME)),
                                stackFrame,
                                new XDebuggerEvaluator.XEvaluationCallback() {
                                  @Override
                                  public void evaluated(@NotNull XValue result) {
                                    popFrame(debugProcess, debuggerContext, stackFrame);
                                  }

                                  @Override
                                  public void errorOccurred(@NotNull final String errorMessage) {
                                    showError(project, DebuggerBundle.message("error.executing.finally", errorMessage),
                                              UIUtil.removeMnemonic(ActionsBundle.actionText(DebuggerActions.POP_FRAME)));
                                  }
                                })) return;
      popFrame(debugProcess, debuggerContext, stackFrame);
    }
    catch (NativeMethodException e2){
      Messages.showMessageDialog(project, DebuggerBundle.message("error.native.method.exception"),
                                 UIUtil.removeMnemonic(ActionsBundle.actionText(DebuggerActions.POP_FRAME)), Messages.getErrorIcon());
    }
    catch (InvalidStackFrameException | VMDisconnectedException ignored) {
    }
  }

  static boolean evaluateFinallyBlocks(Project project,
                                String title,
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
                         DebuggerBundle.message("warning.finally.block.detected") + sb)
            .project(project)
            .icon(Messages.getWarningIcon())
            .yesText(DebuggerBundle.message("button.execute.finally"))
            .noText(DebuggerBundle.message("button.drop.anyway"))
            .cancelText(CommonBundle.message("button.cancel"))
            .doNotAsk(
              new DialogWrapper.DoNotAskOption() {
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

                @NotNull
                @Override
                public String getDoNotShowMessage() {
                  return CommonBundle.message("dialog.options.do.not.show");
                }
              })
            .show();

          switch (res) {
            case Messages.CANCEL:
              return true;
            case Messages.NO:
              break;
            case Messages.YES: // evaluate finally
              evaluateAndAct(project, stackFrame, sb, callback);
              return true;
          }
        }
      }
    }
    return false;
  }

  private static void popFrame(DebugProcessImpl debugProcess, DebuggerContextImpl debuggerContext, JavaStackFrame stackFrame) {
    debugProcess.getManagerThread()
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
                                 UIUtil.removeMnemonic(ActionsBundle.actionText(DebuggerActions.POP_FRAME)),
                                 Messages.getErrorIcon());
    }
  }

  static void showError(Project project, String message, String title) {
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
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
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

  static JavaStackFrame getStackFrame(AnActionEvent e) {
    StackFrameDescriptorImpl descriptor = getSelectedStackFrameDescriptor(e);
    if (descriptor != null) {
      return new JavaStackFrame(descriptor, false);
    }
    return getSelectedStackFrame(e);
  }

  static StackFrameProxyImpl getStackFrameProxy(AnActionEvent e) {
    DebuggerTreeNodeImpl node = getSelectedNode(e.getDataContext());
    if (node != null) {
      NodeDescriptorImpl descriptor = node.getDescriptor();
      if (descriptor instanceof StackFrameDescriptorImpl) {
        return ((StackFrameDescriptorImpl)descriptor).getFrameProxy();
      }
    }
    else {
      JavaStackFrame stackFrame = getSelectedStackFrame(e);
      if (stackFrame != null) {
        return stackFrame.getStackFrameProxy();
      }
    }
    return null;
  }

  @Nullable
  private static StackFrameDescriptorImpl getSelectedStackFrameDescriptor(AnActionEvent e) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(selectedNode != null) {
      NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
      if(descriptor instanceof StackFrameDescriptorImpl) {
        return (StackFrameDescriptorImpl)descriptor;
        //if(selectedNode.getNextSibling() != null) {
        //  return (StackFrameDescriptorImpl)descriptor;
        //}
      }
    }
    return null;
  }

  @Nullable
  private static JavaStackFrame getSelectedStackFrame(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      XDebugSession session = e.getData(XDebugSession.DATA_KEY);
      if (session == null) {
        session = XDebuggerManager.getInstance(project).getCurrentSession();
      }
      if (session != null) {
        XStackFrame frame = session.getCurrentStackFrame();
        if (frame instanceof JavaStackFrame) {
          return ((JavaStackFrame)frame);
        }
      }
    }

    //DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    //StackFrameProxyImpl frameProxy = debuggerContext.getFrameProxy();
    //
    //if(frameProxy == null || frameProxy.isBottom()) {
    //  return null;
    //}
    //return frameProxy;
    return null;
  }

  private static boolean isAtBreakpoint(AnActionEvent e) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(selectedNode != null && selectedNode.getDescriptor() instanceof StackFrameDescriptorImpl) {
      DebuggerTreeNodeImpl parent = selectedNode.getParent();
      if(parent != null) {
        return ((ThreadDescriptorImpl)parent.getDescriptor()).isAtBreakpoint();
      }
    }
    DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    SuspendContextImpl suspendContext = debuggerContext.getSuspendContext();
    return suspendContext != null && debuggerContext.getThreadProxy() == suspendContext.getThread();
  }

  public void update(@NotNull AnActionEvent e) {
    boolean enable = false;

    StackFrameProxyImpl proxy = getStackFrameProxy(e);
    if (proxy != null && !proxy.isBottom() /*&& isAtBreakpoint(e)*/) {
      enable = proxy.getVirtualMachine().canPopFrames();
    }

    if(ActionPlaces.isMainMenuOrActionSearch(e.getPlace()) || ActionPlaces.DEBUGGER_TOOLBAR.equals(e.getPlace())) {
      e.getPresentation().setEnabled(enable);
    }
    else {
      e.getPresentation().setVisible(enable);
    }
  }
}
