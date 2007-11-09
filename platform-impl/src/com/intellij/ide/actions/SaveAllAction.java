
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;

public class SaveAllAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    ApplicationManager.getApplication().saveAll();
  }
}
