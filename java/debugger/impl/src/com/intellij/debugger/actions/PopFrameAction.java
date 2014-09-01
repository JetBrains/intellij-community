/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.actions;

import com.intellij.CommonBundle;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTryStatement;
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
import java.util.List;

public class PopFrameAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final JavaStackFrame stackFrame = getStackFrame(e);
    if(stackFrame == null) {
      return;
    }
    try {
      final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
      final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
      if(debugProcess == null) {
        return;
      }
      if (DebuggerSettings.getInstance().CHECK_FINALLY_ON_POP_FRAME) {
        List<PsiStatement> statements = getFinallyStatements(debuggerContext.getSourcePosition());
        if (!statements.isEmpty()) {
          StringBuilder sb = new StringBuilder();
          for (PsiStatement statement : statements) {
            sb.append("\n").append(statement.getText());
          }
          int res = MessageDialogBuilder
            .yesNoCancel(UIUtil.removeMnemonic(ActionsBundle.actionText(DebuggerActions.POP_FRAME)),
                   DebuggerBundle.message("warning.finally.block.detected") + sb)
            .project(project)
            .icon(Messages.getWarningIcon())
            .yesText(DebuggerBundle.message("button.drop.anyway"))
            .noText(DebuggerBundle.message("button.execute.finally"))
            .cancelText(CommonBundle.message("button.cancel"))
            .doNotAsk(
              new DialogWrapper.DoNotAskOption() {
                @Override
                public boolean isToBeShown() {
                  return DebuggerSettings.getInstance().CHECK_FINALLY_ON_POP_FRAME;
                }

                @Override
                public void setToBeShown(boolean value, int exitCode) {
                  DebuggerSettings.getInstance().CHECK_FINALLY_ON_POP_FRAME = value;
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
            case Messages.CANCEL : return;
            case Messages.OK : break; // drop frame
            case Messages.NO : // evaluate
              JavaDebugProcess process = debugProcess.getXdebugProcess();
              XExpressionImpl expression = XExpressionImpl.fromText(sb.toString());
              expression = XExpressionImpl.changeMode(expression, EvaluationMode.CODE_FRAGMENT);
              XDebuggerEvaluator evaluator = stackFrame.getEvaluator();
              if (evaluator != null) {
                evaluator.evaluate(expression, new XDebuggerEvaluator.XEvaluationCallback() {
                  @Override
                  public void evaluated(@NotNull XValue result) {
                    debugProcess.getManagerThread().schedule(debugProcess.createPopFrameCommand(debuggerContext, stackFrame.getStackFrameProxy()));
                  }

                  @Override
                  public void errorOccurred(@NotNull final String errorMessage) {
                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                      @Override
                      public void run() {
                        Messages
                          .showMessageDialog(project, DebuggerBundle.message("error.executing.finally", errorMessage),
                                             UIUtil.removeMnemonic(ActionsBundle.actionText(DebuggerActions.POP_FRAME)), Messages.getErrorIcon());
                      }
                    });
                  }
                }, stackFrame.getSourcePosition());
                return;
              }
              else {
                Messages.showMessageDialog(project, XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"),
                                           UIUtil.removeMnemonic(ActionsBundle.actionText(DebuggerActions.POP_FRAME)),
                                           Messages.getErrorIcon());
              }
          }
        }
      }
      debugProcess.getManagerThread().schedule(debugProcess.createPopFrameCommand(debuggerContext, stackFrame.getStackFrameProxy()));
    }
    catch (NativeMethodException e2){
      Messages.showMessageDialog(project, DebuggerBundle.message("error.native.method.exception"),
                                 UIUtil.removeMnemonic(ActionsBundle.actionText(DebuggerActions.POP_FRAME)), Messages.getErrorIcon());
    }
    catch (InvalidStackFrameException ignored) {
    }
    catch(VMDisconnectedException vde) {
    }
  }

  private static List<PsiStatement> getFinallyStatements(SourcePosition position) {
    List<PsiStatement> res = new ArrayList<PsiStatement>();
    PsiElement element = position.getFile().findElementAt(position.getOffset());
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
    while (tryStatement != null) {
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock != null) {
        ContainerUtil.addAll(res, finallyBlock.getStatements());
      }
      tryStatement = PsiTreeUtil.getParentOfType(tryStatement, PsiTryStatement.class);
    }
    return res;
  }

  @Nullable
  private static JavaStackFrame getStackFrame(AnActionEvent e) {
    //DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    //if(selectedNode != null) {
    //  NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
    //  if(descriptor instanceof StackFrameDescriptorImpl) {
    //    if(selectedNode.getNextSibling() != null) {
    //      StackFrameDescriptorImpl frameDescriptor = ((StackFrameDescriptorImpl)descriptor);
    //      return frameDescriptor.getFrameProxy();
    //    }
    //    return null;
    //  }
    //  else if(descriptor instanceof ThreadDescriptorImpl || descriptor instanceof ThreadGroupDescriptorImpl) {
    //    return null;
    //  }
    //}

    Project project = e.getProject();
    if (project != null) {
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if (session != null) {
        XStackFrame frame = session.getCurrentStackFrame();
        if (frame instanceof JavaStackFrame) {
          StackFrameProxyImpl proxy = ((JavaStackFrame)frame).getStackFrameProxy();
          return !proxy.isBottom() ? ((JavaStackFrame)frame) : null;
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

  public void update(AnActionEvent e) {
    boolean enable = false;
    JavaStackFrame stackFrame = getStackFrame(e);

    if(stackFrame != null && isAtBreakpoint(e)) {
      VirtualMachineProxyImpl virtualMachineProxy = stackFrame.getStackFrameProxy().getVirtualMachine();
      enable = virtualMachineProxy.canPopFrames();
    }

    if(ActionPlaces.MAIN_MENU.equals(e.getPlace()) || ActionPlaces.DEBUGGER_TOOLBAR.equals(e.getPlace())) {
      e.getPresentation().setEnabled(enable);
    }
    else {
      e.getPresentation().setVisible(enable);
    }
  }
}
