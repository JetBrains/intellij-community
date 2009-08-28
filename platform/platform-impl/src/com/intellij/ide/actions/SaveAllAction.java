
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;

public class SaveAllAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    ApplicationManager.getApplication().saveAll();
  }
}
