package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.debugger.memory.component.MemoryViewManager;

public class ShowTrackedAction extends ToggleAction {
  @Override
  public boolean isSelected(AnActionEvent e) {
    return MemoryViewManager.getInstance().isNeedShowTrackedOnly();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    if (e.getProject() != null) {
      MemoryViewManager.getInstance().setShowTrackedOnly(state);
    }
  }
}
