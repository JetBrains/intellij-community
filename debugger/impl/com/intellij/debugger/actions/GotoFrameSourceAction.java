package com.intellij.debugger.actions;

import com.intellij.debugger.impl.DebuggerContextUtil;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 14, 2004
 * Time: 10:36:59 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class GotoFrameSourceAction extends DebuggerAction{
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    doAction(dataContext);
  }

  protected static void doAction(DataContext dataContext) {
    final Project project = DataKeys.PROJECT.getData(dataContext);
    if(project == null) return;
    StackFrameDescriptorImpl stackFrameDescriptor = getStackFrameDescriptor(dataContext);
    if(stackFrameDescriptor != null) {
      DebuggerContextUtil.setStackFrame(getContextManager(dataContext), stackFrameDescriptor.getFrameProxy());
    }
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(getStackFrameDescriptor(e.getDataContext()) != null);
  }

  private static StackFrameDescriptorImpl getStackFrameDescriptor(DataContext dataContext) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(dataContext);
    if(selectedNode == null) return null;
    if(selectedNode.getDescriptor() == null || !(selectedNode.getDescriptor() instanceof StackFrameDescriptorImpl)) return null;
    return (StackFrameDescriptorImpl)selectedNode.getDescriptor();
  }

}
