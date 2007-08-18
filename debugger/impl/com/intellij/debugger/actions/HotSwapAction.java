package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.HotSwapUI;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jun 26, 2003
 * Time: 12:52:01 PM
 * To change this template use Options | File Templates.
 */
public class HotSwapAction extends AnAction{

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if(project == null) {
      return;
    }

    DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    DebuggerSession session = debuggerManager.getContext().getDebuggerSession();

    if(session != null && session.isAttached()) {
      HotSwapUI.getInstance(project).reloadChangedClasses(session, DebuggerSettings.getInstance().COMPILE_BEFORE_HOTSWAP);
    }
  }

  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if(project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    DebuggerSession session = debuggerManager.getContext().getDebuggerSession();

    e.getPresentation().setEnabled(session != null && session.isAttached() && session.getProcess().canRedefineClasses());
  }
}
