// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

abstract class AbstractEditIntentionSettingsAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(AbstractEditIntentionSettingsAction.class);

  final @NotNull String myFamilyName;
  private final boolean myEnabled;

  protected AbstractEditIntentionSettingsAction(@NotNull IntentionAction action) {
    myFamilyName = action.getFamilyName();
    // needed for checking errors in user written actions
    //noinspection ConstantConditions
    LOG.assertTrue(myFamilyName != null, "action " + action.getClass() + " family returned null");
    myEnabled = !(action instanceof IntentionActionWrapper) ||
                !Objects.equals(action.getFamilyName(), ((IntentionActionWrapper)action).getFullFamilyName());
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myEnabled;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
