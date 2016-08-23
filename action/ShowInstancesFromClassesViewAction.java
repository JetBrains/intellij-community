package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ReferenceType;
import org.jetbrains.debugger.memory.view.InstancesWindow;

public class ShowInstancesFromClassesViewAction extends ClassesActionBase {
  @Override
  protected boolean isEnabled(AnActionEvent e) {
    return super.isEnabled(e) && getSelectedClass(e) != null ;
  }

  @Override
  protected void perform(AnActionEvent e) {
    XDebugSession debugSession = getDebugSession(e);
    ReferenceType selectedClass = getSelectedClass(e);
    if (debugSession != null && selectedClass != null) {
      new InstancesWindow(debugSession, selectedClass).show();
    }
  }
}
