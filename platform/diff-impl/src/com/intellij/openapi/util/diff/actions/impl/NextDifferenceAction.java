package com.intellij.openapi.util.diff.actions.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.DumbAware;

public abstract class NextDifferenceAction extends AnAction implements DumbAware {
  public NextDifferenceAction() {
    super("Next Diff", "Move to the next difference", AllIcons.Actions.MoveDown);
    setEnabledInModalContext(true);
    setShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF).getShortcutSet()); // TODO: remove
  }
}
