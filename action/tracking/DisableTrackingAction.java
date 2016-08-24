package org.jetbrains.debugger.memory.action.tracking;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ReferenceType;
import org.jetbrains.debugger.memory.action.ClassesActionBase;
import org.jetbrains.debugger.memory.component.InstancesTracker;

public class DisableTrackingAction extends ClassesActionBase {
  @Override
  protected boolean isEnabled(AnActionEvent e) {
    Project project = e.getProject();
    ReferenceType selectedClass = getSelectedClass(e);
    return super.isEnabled(e) && selectedClass != null &&
        project != null && InstancesTracker.getInstance(project).isTracked(selectedClass.name());
  }

  @Override
  protected void perform(AnActionEvent e) {
    Project project = e.getProject();
    ReferenceType selectedClass = getSelectedClass(e);
    if(project != null && selectedClass != null) {
      InstancesTracker.getInstance(project).remove(selectedClass);
    }
  }
}
