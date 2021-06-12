// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionGroupUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * This group turns itself into a popup if there's more than one child.
 *
 * @see NonEmptyActionGroup
 * @see NonTrivialActionGroup
 */
public class SmartPopupActionGroup extends DefaultActionGroup implements DumbAware, UpdateInBackground {

  private boolean myCachedIsPopup = true;

  @Override
  public boolean isPopup() {
    return myCachedIsPopup; // called after update()
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    int size = ActionGroupUtil.getActiveActions(this, e).take(3).size();
    e.getPresentation().setEnabledAndVisible(size > 0);
    myCachedIsPopup = size > 2;
  }

  @Override
  public boolean disableIfNoVisibleChildren() {
    return false; // optimization
  }
}
