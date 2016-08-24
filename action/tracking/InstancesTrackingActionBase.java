package org.jetbrains.debugger.memory.action.tracking;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.component.InstancesTracker;
import org.jetbrains.debugger.memory.view.ClassesTable;

import static org.jetbrains.debugger.memory.component.InstancesTracker.TrackingType;

abstract class InstancesTrackingActionBase extends ToggleAction {
  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    TrackingType type = getType();
    ReferenceType selectedClass = getSelectedClass(e);
    Project project = e.getProject();
    if (selectedClass != null && project != null) {
      InstancesTracker tracker = InstancesTracker.getInstance(project);
      TrackingType currentType = tracker.getTrackingType(selectedClass.name());
      if (state) {
        tracker.add(selectedClass, type);
      } else if(type.equals(currentType)) {
        tracker.remove(selectedClass);
      }
    }
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    TrackingType type = getType();
    ReferenceType selectedClass = getSelectedClass(e);
    Project project = e.getProject();
    return selectedClass != null && project != null &&
        type.equals(InstancesTracker.getInstance(project).getTrackingType(selectedClass.name()));
  }

  @NotNull
  protected abstract TrackingType getType();

  @Nullable
  private ReferenceType getSelectedClass(AnActionEvent e) {
    return e.getData(ClassesTable.SELECTED_CLASS_KEY);
  }
}
