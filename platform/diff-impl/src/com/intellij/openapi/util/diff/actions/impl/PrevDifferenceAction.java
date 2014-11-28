package com.intellij.openapi.util.diff.actions.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.DumbAware;

public abstract class PrevDifferenceAction extends AnAction implements DumbAware {
  public PrevDifferenceAction() {
    super("Previous Diff", "Move to the previous difference", AllIcons.Actions.MoveUp);
    setEnabledInModalContext(true);
    setShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_DIFF).getShortcutSet()); // TODO: remove
  }
}
