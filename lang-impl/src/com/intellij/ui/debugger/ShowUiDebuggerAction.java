package com.intellij.ui.debugger;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ShowUiDebuggerAction extends AnAction {

  private UiDebugger myDebugger;

  public void actionPerformed(AnActionEvent e) {
    if (myDebugger == null) {
      myDebugger = new UiDebugger() {
        @Override
        public void dispose() {
          myDebugger = null;
        }
      };
    } else {
      myDebugger.show();
    }
  }
}