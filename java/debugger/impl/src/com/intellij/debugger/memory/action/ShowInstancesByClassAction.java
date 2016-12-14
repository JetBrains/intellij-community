package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.view.InstancesWindow;

public class ShowInstancesByClassAction extends DebuggerTreeAction {
  @Override
  protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    ObjectReference ref = getObjectReference(node);
    if(ref != null) {
      String text = String.format("Show %s Objects...", StringUtil.getShortName(ref.referenceType().name()));
      e.getPresentation().setText(text);
    }

    return ref != null;
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      XDebugSession debugSession = XDebuggerManager.getInstance(project).getCurrentSession();
      ObjectReference ref = getObjectReference(node);
      if(debugSession != null && ref != null){
        ReferenceType referenceType = ref.referenceType();
        new InstancesWindow(debugSession, referenceType::instances, referenceType.name()).show();
      }
    }
  }
}
