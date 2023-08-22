// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionsConfigurable;
import com.intellij.codeInsight.intention.impl.config.IntentionsConfigurableProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class EditIntentionSettingsAction extends AbstractEditIntentionSettingsAction implements HighPriorityAction {
  public EditIntentionSettingsAction(@NotNull IntentionAction action) {
    super(action);
  }

  @Override
  public @NotNull String getText() {
    return CodeInsightBundle.message("edit.intention.settings.intention.text");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    IntentionsConfigurable configurable = (IntentionsConfigurable)ConfigurableExtensionPointUtil
      .createApplicationConfigurableForProvider(IntentionsConfigurableProvider.class);
    if (configurable != null) {
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> {
        SwingUtilities.invokeLater(() -> configurable.selectIntention(myFamilyName));
      });
    }
  }
}
