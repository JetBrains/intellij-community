package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionSettingsConfigurable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class EditIntentionSettingsAction extends AbstractEditIntentionSettingsAction implements HighPriorityAction {
  public EditIntentionSettingsAction(IntentionAction action) {
    super(action);
  }

  @NotNull
  @Override
  public String getText() {
    return "Edit intention settings";
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final IntentionSettingsConfigurable configurable = new IntentionSettingsConfigurable();
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable() {
      @Override
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            configurable.selectIntention(myFamilyName);
          }
        });
      }
    });
  }
}
