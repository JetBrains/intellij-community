package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.view.ClassesTable;

import java.awt.*;

abstract class ClassesActionBase extends AnAction {
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
  ClassesTable getTable(AnActionEvent e) {
    Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    return component == null ? null : (ClassesTable) component;
  }
}
