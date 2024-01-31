// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.UpdateSession;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author gregsh
 */
public class SettingsEntryPointGroup extends DefaultActionGroup {
  @Override
  public @NotNull List<AnAction> postProcessVisibleChildren(@NotNull List<? extends AnAction> visibleChildren,
                                                            @NotNull UpdateSession updateSession) {
    for (AnAction child : visibleChildren) {
      Presentation presentation = updateSession.presentation(child);
      String text = presentation.getText();
      if (text != null && !(text.endsWith("...") || text.endsWith("…")) && !(child instanceof SettingsEntryPointAction.NoDots)) {
        presentation.setText(text + "…");
      }
    }
    return super.postProcessVisibleChildren(visibleChildren, updateSession);
  }
}
