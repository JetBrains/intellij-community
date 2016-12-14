package org.jetbrains.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;

abstract class ShowInstancesAction extends ClassesActionBase {
  @Override
  public void update(AnActionEvent e) {
    boolean enabled = isEnabled(e);
    if (enabled) {
      e.getPresentation().setText(String.format("%s (%d)", getLabel(), getInstancesCount(e)));
    }
  }

  protected abstract String getLabel();

  protected abstract int getInstancesCount(AnActionEvent e);
}
