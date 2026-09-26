// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * @author gregsh
 */
final class SettingsEntryPointGroup extends DefaultActionGroup {
  @Override
  public @Unmodifiable @NotNull List<? extends @NotNull AnAction> postProcessVisibleChildren(@NotNull AnActionEvent e,
                                                                                             @NotNull List<? extends @NotNull AnAction> visibleChildren) {
    for (AnAction child : visibleChildren) {
      Presentation presentation = e.getUpdateSession().presentation(child);
      String text = presentation.getText();
      if (text != null && !(text.endsWith("...") || text.endsWith("…")) && !(child instanceof SettingsEntryPointAction.NoDots)) {
        presentation.setText(text + "…");
      }
    }
    return super.postProcessVisibleChildren(e, visibleChildren);
  }
}
