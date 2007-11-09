package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.help.HelpManager;

public class HelpTopicsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    HelpManager.getInstance().invokeHelp("");
  }
}
