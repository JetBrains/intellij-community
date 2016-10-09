package org.jetbrains.debugger.memory.action.tracking;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ReferenceType;
import org.jetbrains.debugger.memory.action.ClassesActionBase;
import org.jetbrains.debugger.memory.component.InstancesTracker;
import org.jetbrains.debugger.memory.tracking.TrackingType;

public class TrackInstanceCreationAction extends ClassesActionBase {
  @Override
  protected boolean isEnabled(AnActionEvent e) {
    Project project = e.getProject();
    if (!super.isEnabled(e) || project == null) {
      return false;
    }

    InstancesTracker tracker = InstancesTracker.getInstance(project);
    ReferenceType selectedClass = getSelectedClass(e);
    if (selectedClass != null) {
      TrackingType currentType = tracker.getTrackingType(selectedClass.name());
      if (TrackingType.CREATION == currentType) {
        return false;
      }
    }

    return selectedClass != null && !(selectedClass instanceof ArrayType);
  }

  @Override
  protected void perform(AnActionEvent e) {
    Project project = e.getProject();
    ReferenceType selectedClass = getSelectedClass(e);

    if (selectedClass != null && project != null) {
      InstancesTracker tracker = InstancesTracker.getInstance(project);
      tracker.add(selectedClass, TrackingType.CREATION);
    }
  }
}
