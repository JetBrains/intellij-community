package org.jetbrains.debugger.memory.action.tracking;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.component.InstancesTracker;
import org.jetbrains.debugger.memory.tracking.TrackingType;
import org.jetbrains.debugger.memory.view.ClassesTable;

public class TrackInstancesToggleAction extends ToggleAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    ReferenceType selectedClass = getSelectedClass(e);
    if (selectedClass instanceof ArrayType) {
      e.getPresentation().setEnabled(false);
    } else {
      super.update(e);
    }
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    ReferenceType selectedClass = getSelectedClass(e);
    XDebugSession debugSession = getDebugSession(e);
    if (debugSession != null && selectedClass != null) {
      InstancesTracker tracker = InstancesTracker.getInstance(debugSession.getProject());
      return tracker.isTracked(selectedClass.name());
    }

    return false;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    ReferenceType selectedClass = getSelectedClass(e);
    XDebugSession debugSession = getDebugSession(e);
    if (selectedClass != null && debugSession != null) {
      InstancesTracker tracker = InstancesTracker.getInstance(debugSession.getProject());
      boolean isAlreadyTracked = tracker.isTracked(selectedClass.name());

      if (isAlreadyTracked && !state) {
        tracker.remove(selectedClass.name());
      }

      if (!isAlreadyTracked && state) {
        tracker.add(selectedClass.name(), TrackingType.CREATION);
      }
    }
  }

  @Nullable
  private ReferenceType getSelectedClass(AnActionEvent e) {
    return e.getData(ClassesTable.SELECTED_CLASS_KEY);
  }

  @Nullable
  private XDebugSession getDebugSession(AnActionEvent e) {
    return e.getData(ClassesTable.DEBUG_SESSION_KEY);
  }
}
