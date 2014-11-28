package com.intellij.openapi.util.diff.actions.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.DumbAware;

public abstract class PrevChangeAction extends AnAction implements DumbAware {
  public PrevChangeAction() {
    super("Previous Change", "Compare Previous File", AllIcons.Actions.Prevfile);
    setEnabledInModalContext(true);
    setShortcutSet(ActionManager.getInstance().getAction("Diff.PrevChange").getShortcutSet()); // TODO: remove
  }
}
