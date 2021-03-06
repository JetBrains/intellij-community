// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * This group hides itself when there's no registered children.
 *
 * @see SmartPopupActionGroup
 * @see NonTrivialActionGroup
 */
public class NonEmptyActionGroup extends DefaultActionGroup implements DumbAware {
  public NonEmptyActionGroup() {
    super();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabledAndVisible(getChildrenCount() > 0);
  }

  @Override
  public boolean hideIfNoVisibleChildren() {
    return true;
  }
}
