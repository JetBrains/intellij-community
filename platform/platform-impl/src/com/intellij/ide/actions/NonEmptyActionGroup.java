// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * This group hides itself when there are no registered children.
 *
 * @see SmartPopupActionGroup
 * @see NonTrivialActionGroup
 */
public class NonEmptyActionGroup extends DefaultActionGroup implements DumbAware {
  public NonEmptyActionGroup() {
    super();
    getTemplatePresentation().setHideGroupIfEmpty(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return getClass() == NonEmptyActionGroup.class ? ActionUpdateThread.BGT : super.getActionUpdateThread();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabledAndVisible(!event.getUpdateSession().children(this).isEmpty());
  }
}
