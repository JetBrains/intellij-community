package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ReferenceType;
import org.jetbrains.debugger.memory.view.ClassesTable;
import org.jetbrains.debugger.memory.view.InstancesWindow;

public class ShowInstancesFromClassesViewAction extends ShowInstancesAction {
  private static final String POPUP_ELEMENT_LABEL = "Show Instances";
  @Override
  protected boolean isEnabled(AnActionEvent e) {
    return super.isEnabled(e) && getSelectedClass(e) != null;
  }

  @Override
  protected void perform(AnActionEvent e) {
    XDebugSession debugSession = getDebugSession(e);
    ReferenceType selectedClass = getSelectedClass(e);
    if (debugSession != null && selectedClass != null) {
      new InstancesWindow(debugSession, selectedClass::instances, selectedClass.name()).show();
    }
  }

  @Override
  protected String getLabel() {
    return POPUP_ELEMENT_LABEL;
  }

  @Override
  protected int getInstancesCount(AnActionEvent e) {
    ClassesTable.ReferenceCountProvider countProvider = e.getData(ClassesTable.REF_COUNT_PROVIDER_KEY);
    ReferenceType selectedClass = getSelectedClass(e);
    if (countProvider == null || selectedClass == null) {
      return -1;
    }

    return countProvider.getTotalCount(selectedClass);
  }
}
