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
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.NativeMethodException;
import com.sun.jdi.VMDisconnectedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PopFrameAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    StackFrameProxyImpl stackFrame = getStackFrameProxy(e);
    if(stackFrame == null) {
      return;
    }
    try {
      DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
      DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
      if(debugProcess == null) {
        return;
      }
      if (DebuggerSettings.getInstance().CHECK_FINALLY_ON_POP_FRAME && isInTryWithFinally(debuggerContext.getSourcePosition())) {
        int res = MessageDialogBuilder
          .yesNo(UIUtil.removeMnemonic(ActionsBundle.actionText(DebuggerActions.POP_FRAME)),
                 DebuggerBundle.message("warning.finally.block.detected"))
          .project(project)
          .icon(Messages.getWarningIcon())
          .yesText(DebuggerBundle.message("button.drop.anyway"))
          .noText(CommonBundle.message("button.cancel"))
          .doNotAsk(
            new DialogWrapper.DoNotAskOption() {
              @Override
              public boolean isToBeShown() {
                return DebuggerSettings.getInstance().CHECK_FINALLY_ON_POP_FRAME;
              }

              @Override
              public void setToBeShown(boolean value, int exitCode) {
                DebuggerSettings.getInstance().CHECK_FINALLY_ON_POP_FRAME = false;
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

        if (res == Messages.NO) {
          return;
        }
      }
      debugProcess.getManagerThread().schedule(debugProcess.createPopFrameCommand(debuggerContext, stackFrame));
    }
    catch (NativeMethodException e2){
      Messages.showMessageDialog(project, DebuggerBundle.message("error.native.method.exception"), UIUtil.removeMnemonic(
        ActionsBundle.actionText(DebuggerActions.POP_FRAME)), Messages.getErrorIcon());
    }
    catch (InvalidStackFrameException ignored) {
    }
    catch(VMDisconnectedException vde) {
    }
  }

  private static boolean isInTryWithFinally(SourcePosition position) {
    PsiElement element = position.getFile().findElementAt(position.getOffset());
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
    while (tryStatement != null) {
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock != null && finallyBlock.getStatements().length > 0) {
        return true;
      }
      tryStatement = PsiTreeUtil.getParentOfType(tryStatement, PsiTryStatement.class);
    }
    return false;
  }

  @Nullable
  private static StackFrameProxyImpl getStackFrameProxy(AnActionEvent e) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(selectedNode != null) {
      NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
      if(descriptor instanceof StackFrameDescriptorImpl) {
        if(selectedNode.getNextSibling() != null) {
          StackFrameDescriptorImpl frameDescriptor = ((StackFrameDescriptorImpl)descriptor);
          return frameDescriptor.getFrameProxy();
        }
        return null;
      }
      else if(descriptor instanceof ThreadDescriptorImpl || descriptor instanceof ThreadGroupDescriptorImpl) {
        return null;
      }
    }

    Project project = e.getProject();
    if (project != null) {
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if (session != null) {
        XStackFrame frame = session.getCurrentStackFrame();
        if (frame instanceof JavaStackFrame) {
          StackFrameProxyImpl proxy = ((JavaStackFrame)frame).getStackFrameProxy();
          return !proxy.isBottom() ? proxy : null;
        }
      }
    }

    DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    StackFrameProxyImpl frameProxy = debuggerContext.getFrameProxy();

    if(frameProxy == null || frameProxy.isBottom()) {
      return null;
    }
    return frameProxy;
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
    StackFrameProxyImpl stackFrameProxy = getStackFrameProxy(e);

    if(stackFrameProxy != null && isAtBreakpoint(e)) {
      VirtualMachineProxyImpl virtualMachineProxy = stackFrameProxy.getVirtualMachine();
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
