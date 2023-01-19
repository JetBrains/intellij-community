// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileTemplateSeparator extends ActionGroup {

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return new AnAction[]{Separator.create(shouldShowNamedSeparator(e) ? IdeBundle.message("action.separator.file.templates") : null)};
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static boolean shouldShowNamedSeparator(@Nullable AnActionEvent e) {
    if (e == null || e.isFromContextMenu()) return false; // popup menus show the name, but no separator currently, which looks ugly
    return new CreateFromTemplateGroup().getChildren(e).length > 0;
  }
}
