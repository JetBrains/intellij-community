/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.NativeMethodException;
import com.sun.jdi.VMDisconnectedException;
import org.jetbrains.annotations.Nullable;

public class PopFrameAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
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
      debugProcess.getManagerThread().invokeLater(debugProcess.createPopFrameCommand(debuggerContext, stackFrame));
    }
    catch (NativeMethodException e2){
      Messages.showMessageDialog(project, DebuggerBundle.message("error.native.method.exception"), ActionsBundle.actionText(DebuggerActions.POP_FRAME), Messages.getErrorIcon());
    }
    catch (InvalidStackFrameException ignored) {
    }
    catch(VMDisconnectedException vde) {
    }
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
