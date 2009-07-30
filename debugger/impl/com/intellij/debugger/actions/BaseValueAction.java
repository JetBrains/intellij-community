/*
 * @author max
 */
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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.util.ProgressWindowWithNotification;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

/*
 * @author Jeka
 */
public abstract class BaseValueAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    final DataContext actionContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(actionContext);
    final Value value = getValue(actionContext);
    if (value == null) {
      return;
    }

    DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    if(debuggerManager != null) {
      final DebuggerContextImpl debuggerContext = debuggerManager.getContext();

      if(debuggerContext != null && debuggerContext.getDebuggerSession() != null) {
        final ProgressWindowWithNotification progressWindow = new ProgressWindowWithNotification(true, project);
        SuspendContextCommandImpl copyValueAction = new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
          public Priority getPriority() {
            return Priority.HIGH;
          }

          public void contextAction() throws Exception {
            //noinspection HardCodedStringLiteral
            progressWindow.setText(DebuggerBundle.message("progress.evaluating", "toString()"));

            final String valueAsString = DebuggerUtilsEx.getValueOrErrorAsString(debuggerContext.createEvaluationContext(), value);

            if (progressWindow.isCanceled()) return;

            DebuggerInvocationUtil.swingInvokeLater(project, new Runnable() {
              public void run() {
                String text = valueAsString;

                if (text == null) text = "";

                processText(project, text);
              }
            });
          }
        };
        progressWindow.setTitle(DebuggerBundle.message("title.evaluating"));
        debuggerContext.getDebugProcess().getManagerThread().startProgress(copyValueAction, progressWindow);
      }
    }
  }

  protected abstract void processText(final Project project, String text);

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Value value = getValue(e.getDataContext());
    presentation.setEnabled(value != null);
    presentation.setVisible(value != null);
  }

  @Nullable
  public static Value getValue(DataContext context) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(context);
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