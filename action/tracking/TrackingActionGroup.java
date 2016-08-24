package org.jetbrains.debugger.memory.action.tracking;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.debugger.memory.view.ClassesTable;

public class TrackingActionGroup extends DefaultActionGroup implements DumbAware{
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(e.getData(ClassesTable.SELECTED_CLASS_KEY) != null);
  }
}
