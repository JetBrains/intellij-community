package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ReferenceType;
import org.jetbrains.debugger.memory.utils.InstancesProvider;
import org.jetbrains.debugger.memory.view.ClassesTable;
import org.jetbrains.debugger.memory.view.InstancesWindow;

public class ShowNewInstancesAction extends ShowInstancesAction {
  private static final String POPUP_ELEMENT_LABEL = "Show New Instances";

  @Override
  protected boolean isEnabled(AnActionEvent e) {
    XDebugSession session = getDebugSession(e);
    ReferenceType selectedClass = getSelectedClass(e);
    InstancesProvider provider = e.getData(ClassesTable.NEW_INSTANCES_PROVIDER_KEY);
    return super.isEnabled(e) && session != null && selectedClass != null && provider != null;
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

    return countProvider.getNewInstancesCount(selectedClass);
  }

  @Override
  protected void perform(AnActionEvent e) {
    XDebugSession session = getDebugSession(e);
    ReferenceType selectedClass = getSelectedClass(e);
    InstancesProvider provider = e.getData(ClassesTable.NEW_INSTANCES_PROVIDER_KEY);
    if (selectedClass != null && provider != null && session != null) {
      new InstancesWindow(session, provider, selectedClass.name()).show();
    }
  }
}
