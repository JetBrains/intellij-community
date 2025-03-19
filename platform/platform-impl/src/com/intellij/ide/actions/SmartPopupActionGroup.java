// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionGroupUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * This group turns itself into a popup if there's more than {@link #getChildrenCountThreshold()} children
 *
 * @see NonEmptyActionGroup
 * @see NonTrivialActionGroup
 */
public class SmartPopupActionGroup extends DefaultActionGroup implements DumbAware {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected int getChildrenCountThreshold() {
    return 2;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    int size = ActionGroupUtil.getVisibleActions(this, e).take(getChildrenCountThreshold() + 1).size();
    e.getPresentation().setEnabledAndVisible(size > 0);
    e.getPresentation().setPopupGroup(size > getChildrenCountThreshold());
    e.getPresentation().setDisableGroupIfEmpty(false);
  }
}
