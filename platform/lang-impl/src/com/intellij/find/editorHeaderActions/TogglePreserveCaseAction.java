// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.FindBundle;
import com.intellij.find.SearchSession;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class TogglePreserveCaseAction extends EditorHeaderToggleAction implements Embeddable, TooltipDescriptionProvider {
  public TogglePreserveCaseAction() {
    super(FindBundle.message("find.options.replace.preserve.case"),
          AllIcons.Actions.PreserveCase,
          AllIcons.Actions.PreserveCaseHover,
          AllIcons.Actions.PreserveCaseSelected);
  }

  @Override
  protected boolean isSelected(@NotNull SearchSession session) {
    return session.getFindModel().isPreserveCase();
  }

  @Override
  protected void setSelected(@NotNull SearchSession session, boolean selected) {
    session.getFindModel().setPreserveCase(selected);
  }
}
