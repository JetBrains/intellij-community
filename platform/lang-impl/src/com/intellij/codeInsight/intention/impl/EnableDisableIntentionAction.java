package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class EnableDisableIntentionAction extends AbstractEditIntentionSettingsAction {
  private static final Logger LOG = Logger.getInstance(EnableDisableIntentionAction.class);

  private final IntentionManagerSettings mySettings = IntentionManagerSettings.getInstance();
  private final IntentionAction myAction;

  public EnableDisableIntentionAction(IntentionAction action) {
    super(action);
    myAction = action;
    // needed for checking errors in user written actions
    //noinspection ConstantConditions
    LOG.assertTrue(myFamilyName != null, "action " + action.getClass() + " family returned null");
  }

  @Override
  @NotNull
  public String getText() {
    return mySettings.isEnabled(myAction) ?
           CodeInsightBundle.message("disable.intention.action", myFamilyName) :
           CodeInsightBundle.message("enable.intention.action", myFamilyName);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    mySettings.setEnabled(myAction, !mySettings.isEnabled(myAction));
  }

  @Override
  public String toString() {
    return getText();
  }
}
