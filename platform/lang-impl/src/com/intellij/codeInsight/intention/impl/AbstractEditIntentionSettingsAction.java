package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

abstract class AbstractEditIntentionSettingsAction implements IntentionAction {
  final String myFamilyName;
  private final boolean myDisabled;

  public AbstractEditIntentionSettingsAction(IntentionAction action) {
    myFamilyName = action.getFamilyName();
    myDisabled = action instanceof IntentionActionWrapper &&
                 Comparing.equal(action.getFamilyName(), ((IntentionActionWrapper)action).getFullFamilyName());
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return !myDisabled;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
