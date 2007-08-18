/*
 * Class NewWatchAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.debugger.ui.impl.MainWatchPanel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;

public class NewWatchAction extends DebuggerAction {
  public void actionPerformed(final AnActionEvent e) {
    Project project = DataKeys.PROJECT.getData(e.getDataContext());
    if(project == null) return;

    final MainWatchPanel watchPanel = DebuggerPanelsManager.getInstance(project).getWatchPanel();
    if (watchPanel != null) {
      watchPanel.newWatch();
    }
  }
}
