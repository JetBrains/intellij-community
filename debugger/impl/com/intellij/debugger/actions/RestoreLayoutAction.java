/*
 * Class NewWatchAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.debugger.ui.DebuggerSessionTab;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;

public class RestoreLayoutAction extends DebuggerAction {
  public void actionPerformed(final AnActionEvent e) {
    Project project = DataKeys.PROJECT.getData(e.getDataContext());
    if(project == null) return;

    final DebuggerSessionTab sessionTab = DebuggerPanelsManager.getInstance(project).getSessionTab();
    if (sessionTab != null) {
      sessionTab.restoreLayout();
    }
  }
}