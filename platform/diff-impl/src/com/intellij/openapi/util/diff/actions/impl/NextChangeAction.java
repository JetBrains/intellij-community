package com.intellij.openapi.util.diff.actions.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.DumbAware;

public abstract class NextChangeAction extends AnAction implements DumbAware {
  public NextChangeAction() {
    super("Next Change", "Compare Next File", AllIcons.Actions.Nextfile);
    setEnabledInModalContext(true);
    setShortcutSet(ActionManager.getInstance().getAction("Diff.NextChange").getShortcutSet()); // TODO: remove
  }
}
