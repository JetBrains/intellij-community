// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public class FocusOppositePaneAction extends AnAction implements DumbAware {
  protected final boolean myScrollToPosition;

  public FocusOppositePaneAction() {
    this(false);
  }

  public FocusOppositePaneAction(boolean scrollToPosition) {
    myScrollToPosition = scrollToPosition;
    ActionUtil.copyFrom(this, getActionId());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    throw new UnsupportedOperationException();
  }

  public void install(@NotNull JComponent component) {
    registerCustomShortcutSet(getShortcutSet(), component);
  }

  private @NonNls @NotNull String getActionId() {
    return myScrollToPosition ? "Diff.FocusOppositePaneAndScroll" : "Diff.FocusOppositePane";
  }
}