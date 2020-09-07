// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.find.SearchSession;
import com.intellij.find.impl.RegExHelpPopup;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import com.intellij.openapi.actionSystem.ex.TooltipLinkProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ToggleRegex extends EditorHeaderToggleAction implements Embeddable, TooltipLinkProvider, TooltipDescriptionProvider {
  public ToggleRegex() {
    super(FindBundle.message("find.regex"),
          AllIcons.Actions.Regex,
          AllIcons.Actions.RegexHovered,
          AllIcons.Actions.RegexSelected);
  }

  @Override
  public TooltipLink getTooltipLink(@Nullable JComponent owner) {
    return new TooltipLink(FindBundle.message("find.regex.help.link"), RegExHelpPopup.createRegExLinkRunnable(owner));
  }

  @Override
  protected boolean isSelected(@NotNull SearchSession session) {
    return session.getFindModel().isRegularExpressions();
  }

  @Override
  protected void setSelected(@NotNull SearchSession session, boolean selected) {
    FindModel findModel = session.getFindModel();
    findModel.setRegularExpressions(selected);
    if (selected) {
      findModel.setWholeWordsOnly(false);
    }
    FindSettings.getInstance().setLocalRegularExpressions(selected);
  }
}
