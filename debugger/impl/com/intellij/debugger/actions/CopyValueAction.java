package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.util.ProgressWindowWithNotification;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Value;

import java.awt.datatransfer.StringSelection;

/*
 * Class SetValueAction
 * @author Jeka
 */
public class CopyValueAction extends DebuggerAction {

  public void actionPerformed(AnActionEvent e) {
    final Project project = DataKeys.PROJECT.getData(e.getDataContext());
    final Value value = getValue(e);
    if (value == null) {
      return;
    }

    DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    if(debuggerManager != null) {
      final DebuggerContextImpl context = debuggerManager.getContext();

      if(context != null && context.getDebuggerSession() != null) {
        final ProgressWindowWithNotification progressWindow = new ProgressWindowWithNotification(true, project);
        SuspendContextCommandImpl copyValueAction = new SuspendContextCommandImpl(context.getSuspendContext()) {
          public void contextAction() throws Exception {
            //noinspection HardCodedStringLiteral
            progressWindow.setText(DebuggerBundle.message("progress.evaluating", "toString()"));

            final String valueAsString = DebuggerUtilsEx.getValueOrErrorAsString(context.createEvaluationContext(), value);

            if (progressWindow.isCanceled()) return;

            DebuggerInvocationUtil.invokeLater(project, new Runnable() {
              public void run() {
                String text = valueAsString;

                if (text == null) text = "";

                CopyPasteManager.getInstance().setContents(new StringSelection(text));
              }
            });
          }
        };
        progressWindow.setTitle(DebuggerBundle.message("title.evaluating"));
        context.getDebugProcess().getManagerThread().startProgress(copyValueAction, progressWindow);
      }
    }

  }


  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Value value = getValue(e);
    presentation.setEnabled(value != null);
    presentation.setVisible(value != null);
  }

  private Value getValue(AnActionEvent e) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if (selectedNode == null) {
      return null;
    }
    NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
    if (!(descriptor instanceof ValueDescriptorImpl)) {
      return null;
    }
    return ((ValueDescriptorImpl)descriptor).getValue();
  }
}
