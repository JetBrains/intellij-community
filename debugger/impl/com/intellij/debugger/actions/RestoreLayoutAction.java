/*
 * Class NewWatchAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.ui.CompletedInputDialog;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.debugger.ui.DebuggerSessionTab;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.impl.WatchPanel;
import com.intellij.debugger.ui.impl.MainWatchPanel;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;

public class RestoreLayoutAction extends DebuggerAction {
  public void actionPerformed(final AnActionEvent e) {
    Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
    if(project == null) return;

    final DebuggerSessionTab sessionTab = DebuggerPanelsManager.getInstance(project).getSessionTab();
    if (sessionTab != null) {
      sessionTab.restoreLayout();
    }
  }
}