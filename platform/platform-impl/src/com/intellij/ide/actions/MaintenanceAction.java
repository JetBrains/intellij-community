package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopupFactory;

public class MaintenanceAction extends AnAction {

  public MaintenanceAction() {
    super("Maintenance");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("MaintenanceGroup");
    JBPopupFactory.getInstance().
      createActionGroupPopup("Maintenance", group, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, true).
      showInFocusCenter();
  }
}