package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.debugger.memory.component.MemoryViewManager;

public class ShowClassesWithDiff extends ToggleAction {
  @Override
  public boolean isSelected(AnActionEvent e) {
    Project project = e.getProject();
    return project != null && MemoryViewManager.getInstance(e.getProject()).isNeedShowDiffOnly();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project != null) {
      MemoryViewManager.getInstance(project).setShowDiffOnly(state);
    }
  }
}
