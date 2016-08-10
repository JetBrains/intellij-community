package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.sun.jdi.ReferenceType;
import org.jetbrains.debugger.memory.view.ClassesTable;
import org.jetbrains.debugger.memory.view.InstancesWindow;

public class ShowInstancesFromClassesViewAction extends ClassesActionBase {
  @Override
  protected boolean isEnabled(AnActionEvent e) {
    return super.isEnabled(e);
  }

  @Override
  protected void perform(AnActionEvent e) {
    ClassesTable classesView = getTable(e);
    if(classesView != null){
      ReferenceType selectedClass = classesView.getSelectedClass();
      if(selectedClass != null) {
        new InstancesWindow(classesView.getDebugSession(), selectedClass).show();
      }
    }
  }
}
