package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.view.ClassesTable;

public abstract class ClassesActionBase extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    perform(e);
  }

  protected boolean isEnabled(AnActionEvent e) {
    Project project = e.getProject();
    return project != null && !project.isDisposed();
  }

  protected abstract void perform(AnActionEvent e);

  @Nullable
  protected ReferenceType getSelectedClass(AnActionEvent e) {
    return e.getData(ClassesTable.SELECTED_CLASS_KEY);
  }

  @Nullable
  XDebugSession getDebugSession(AnActionEvent e) {
    return e.getData(ClassesTable.DEBUG_SESSION_KEY);
  }
}
