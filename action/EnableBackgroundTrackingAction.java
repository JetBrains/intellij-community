package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.debugger.memory.component.InstancesTracker;

public class EnableBackgroundTrackingAction extends ToggleAction{

  @Override
  public boolean isSelected(AnActionEvent e) {
    Project project = e.getProject();
    return project != null && InstancesTracker.getInstance(project).isBackgroundTrackingEnabled();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project != null) {
      InstancesTracker.getInstance(project).setBackgroundTackingEnabled(state);
    }
  }
}
