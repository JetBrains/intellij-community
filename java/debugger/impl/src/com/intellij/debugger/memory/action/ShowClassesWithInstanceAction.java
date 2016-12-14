package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.debugger.memory.component.MemoryViewManager;

public class ShowClassesWithInstanceAction extends ToggleAction {
  @Override
  public boolean isSelected(AnActionEvent e) {
    return MemoryViewManager.getInstance().isNeedShowInstancesOnly();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project != null) {
      MemoryViewManager.getInstance().setShowWithInstancesOnly(state);
    }
  }
}
