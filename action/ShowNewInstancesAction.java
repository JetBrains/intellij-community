package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ReferenceType;
import org.jetbrains.debugger.memory.component.InstancesTracker;
import org.jetbrains.debugger.memory.utils.InstancesProvider;
import org.jetbrains.debugger.memory.view.ClassesTable;
import org.jetbrains.debugger.memory.view.InstancesWindow;

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
    XDebugSession session = getDebugSession(e);
    ReferenceType selectedClass = getSelectedClass(e);
    InstancesProvider provider = e.getData(ClassesTable.NEW_INSTANCES_PROVIDER_KEY);
    if(selectedClass != null && provider != null && session != null) {
      new InstancesWindow(session, provider, selectedClass.name()).show();
    }
  }
}
