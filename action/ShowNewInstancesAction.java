package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ReferenceType;
import org.jetbrains.debugger.memory.component.InstancesTracker;

public class ShowNewInstancesAction extends ClassesActionBase {
  @Override
  protected boolean isEnabled(AnActionEvent e) {
    Project project = e.getProject();
    ReferenceType selectedClass = getSelectedClass(e);
    return super.isEnabled(e) && selectedClass != null &&
        project != null && InstancesTracker.getInstance(project).isTracked(selectedClass.name());
  }

  @Override
  protected void perform(AnActionEvent e) {

  }
}
