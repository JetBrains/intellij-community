// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.FindBundle;
import com.intellij.find.FindSettings;
import com.intellij.find.SearchSession;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import org.jetbrains.annotations.NotNull;

public final class ToggleWholeWordsOnlyAction extends EditorHeaderToggleAction implements Embeddable, TooltipDescriptionProvider {
  public ToggleWholeWordsOnlyAction() {
    super(FindBundle.message("find.whole.words"),
          AllIcons.Actions.Words,
          AllIcons.Actions.WordsHovered,
          AllIcons.Actions.WordsSelected);
  }

  @Override
  protected boolean isSelected(@NotNull SearchSession session) {
    return session.getFindModel().isWholeWordsOnly();
  }

  @Override
  protected void setSelected(@NotNull SearchSession session, boolean selected) {
    FindSettings.getInstance().setLocalWholeWordsOnly(selected);
    session.getFindModel().setWholeWordsOnly(selected);
  }
}
