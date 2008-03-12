package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.sun.jdi.request.EventRequest;

/**
 * User: lex
 * Date: Sep 26, 2003
 * Time: 7:35:09 PM
 */
public class ResumeThreadAction extends DebuggerAction{
  public void actionPerformed(final AnActionEvent e) {
    DebuggerTreeNodeImpl[] selectedNode = getSelectedNodes(e.getDataContext());
    final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();

    //noinspection ConstantConditions
    for (final DebuggerTreeNodeImpl debuggerTreeNode : selectedNode) {
      final ThreadDescriptorImpl threadDescriptor = ((ThreadDescriptorImpl)debuggerTreeNode.getDescriptor());

      if (threadDescriptor.isSuspended()) {
        final ThreadReferenceProxyImpl thread = threadDescriptor.getThreadReference();
        debugProcess.getManagerThread().invokeLater(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
          public void contextAction() throws Exception {
            debugProcess.createResumeThreadCommand(getSuspendContext(), thread).run();
            debuggerTreeNode.calcValue();
          }
        });
      }
    }
  }

  public void update(AnActionEvent e) {
    DebuggerTreeNodeImpl[] selectedNodes = getSelectedNodes(e.getDataContext());

    boolean visible = false;
    boolean enabled = false;
    String text = DebuggerBundle.message("action.resume.thread.text.resume");

    if(selectedNodes != null && selectedNodes.length > 0){
      visible = true;
      enabled = true;
      for (DebuggerTreeNodeImpl selectedNode : selectedNodes) {
        final NodeDescriptorImpl threadDescriptor = selectedNode.getDescriptor();
        if (!(threadDescriptor instanceof ThreadDescriptorImpl) || !((ThreadDescriptorImpl)threadDescriptor).isSuspended()) {
          visible = false;
          break;
        }
      }
      if (visible) {
        for (DebuggerTreeNodeImpl selectedNode : selectedNodes) {
          final ThreadDescriptorImpl threadDescriptor = (ThreadDescriptorImpl)selectedNode.getDescriptor();
          if (threadDescriptor.getSuspendContext().getSuspendPolicy() == EventRequest.SUSPEND_ALL && !threadDescriptor.isFrozen()) {
            enabled = false;
            break;
          }
          else {
            if (threadDescriptor.isFrozen()) {
              text = DebuggerBundle.message("action.resume.thread.text.unfreeze");
            }
          }
        }
      }
    }
    final Presentation presentation = e.getPresentation();
    presentation.setText(text);
    presentation.setVisible(visible);
    presentation.setEnabled(enabled);
  }
}
