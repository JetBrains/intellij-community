/*
 * Class EvaluateAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.ValueHint;
import com.intellij.debugger.ui.ValueLookupManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;

public class QuickEvaluateAction extends AnAction {

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = event.getData(DataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();

    boolean toEnable = debuggerSession != null && debuggerSession.isPaused();
    presentation.setEnabled(toEnable);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(toEnable);
    }
    else {
      presentation.setVisible(true);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project == null) { return; }

    DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
    if (debuggerSession == null || !debuggerSession.isPaused()) return;

    Editor editor = e.getData(DataKeys.EDITOR);

    if(editor != null) {
      LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
      ValueLookupManager.getInstance(project).showHint(editor, editor.logicalPositionToXY(logicalPosition), ValueHint.MOUSE_CLICK_HINT);
    }
  }
}
