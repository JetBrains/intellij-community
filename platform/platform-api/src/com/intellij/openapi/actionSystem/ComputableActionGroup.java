package com.intellij.openapi.actionSystem;

import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ComputableActionGroup extends ActionGroup implements DumbAware {
  private AnAction[] myChildren;

  protected ComputableActionGroup() {
  }

  protected ComputableActionGroup(boolean popup) {
    super(null, popup);
  }

  @Override
  public boolean hideIfNoVisibleChildren() {
    return true;
  }

  @Override
  @NotNull
  public final AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) {
      return EMPTY_ARRAY;
    }

    if (myChildren == null) {
      myChildren = computeChildren(e.getActionManager());
    }
    return myChildren;
  }

  @NotNull
  protected abstract AnAction[] computeChildren(@NotNull ActionManager manager);
}