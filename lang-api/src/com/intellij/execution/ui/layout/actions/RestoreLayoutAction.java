/*
 * Class NewWatchAction
 * @author Jeka
 */
package com.intellij.execution.ui.layout.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class RestoreLayoutAction extends AnAction {

  public void actionPerformed(final AnActionEvent e) {
    ToggleToolbarLayoutAction.getRunnerUi(e).restoreLayout();
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(ToggleToolbarLayoutAction.getRunnerUi(e) != null);
  }
}