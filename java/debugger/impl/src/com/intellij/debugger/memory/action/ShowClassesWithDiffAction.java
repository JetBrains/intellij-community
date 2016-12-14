package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.debugger.memory.component.MemoryViewManager;

public class ShowClassesWithDiffAction extends ToggleAction {
  @Override
  public boolean isSelected(AnActionEvent e) {
    return MemoryViewManager.getInstance().isNeedShowDiffOnly();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project != null) {
      MemoryViewManager.getInstance().setShowDiffOnly(state);
    }
  }
}
