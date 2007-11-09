package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;

public abstract class CloseTabToolbarAction extends AnAction {
  public CloseTabToolbarAction() {
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ACTIVE_TAB));
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(IconLoader.getIcon("/actions/cancel.png"));
    presentation.setText(CommonBundle.getCloseButtonText());
    presentation.setDescription(null);
  }
}
