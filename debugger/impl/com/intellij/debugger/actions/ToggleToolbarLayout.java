package com.intellij.debugger.actions;

import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;

public class ToggleToolbarLayout extends ToggleAction {

  public void update(final AnActionEvent e) {
    if (getRunnerUi(e) == null) {
      e.getPresentation().setEnabled(false);
    } else {
      super.update(e);
    }
  }

  public boolean isSelected(final AnActionEvent e) {
    final RunnerContentUi ui = getRunnerUi(e);
    return ui != null ? ui.isHorizontalToolbar() : false;
  }

  public void setSelected(final AnActionEvent e, final boolean state) {
    getRunnerUi(e).setHorizontalToolbar(state);
  }

  private static RunnerContentUi getRunnerUi(final AnActionEvent e) {
    return RunnerContentUi.KEY.getData(e.getDataContext());
  }

}
