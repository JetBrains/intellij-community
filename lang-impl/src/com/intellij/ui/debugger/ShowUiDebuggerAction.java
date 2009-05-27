package com.intellij.ui.debugger;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ShowUiDebuggerAction extends AnAction {

  private UiDebugger myDebugger;

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText("UI Debugger");
  }

  public void actionPerformed(AnActionEvent e) {
    if (myDebugger == null) {
      myDebugger = new UiDebugger() {
        @Override
        public void dispose() {
          super.dispose();
          myDebugger = null;
        }
      };
    } else {
      myDebugger.show();
    }
  }
}