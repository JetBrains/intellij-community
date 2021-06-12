// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionGroupUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * This group hides itself when there's no enabled and visible child.
 *
 * @see SmartPopupActionGroup
 * @see NonEmptyActionGroup
 *
 * @author gregsh
 */
public class NonTrivialActionGroup extends DefaultActionGroup implements DumbAware, UpdateInBackground {
  public NonTrivialActionGroup() {
    super();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(!ActionGroupUtil.isGroupEmpty(this, e));
  }
}
