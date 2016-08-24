package org.jetbrains.debugger.memory.action.tracking;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ReferenceType;
import org.jetbrains.debugger.memory.action.ClassesActionBase;
import org.jetbrains.debugger.memory.component.InstancesTracker;

public class RetainReferenceBasedTrackingAction extends ClassesActionBase {
  @Override
  protected void perform(AnActionEvent e) {
    Project project = e.getProject();
    ReferenceType selectedClass = getSelectedClass(e);
    if (project != null && selectedClass != null) {
      InstancesTracker.getInstance(project).add(selectedClass, InstancesTracker.TrackingType.RETAIN);
    }
  }
}
