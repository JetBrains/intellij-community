package com.intellij.openapi.util.diff.actions.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.DumbAware;

public abstract class GoToChangePopupAction extends AnAction implements DumbAware {
  public GoToChangePopupAction() {
    super("Go To Change", "Choose Change To Compare", AllIcons.Actions.ShowAsTree);
    setEnabledInModalContext(true);
    setShortcutSet(ActionManager.getInstance().getAction("GotoClass").getShortcutSet()); // TODO: remove
  }
}
