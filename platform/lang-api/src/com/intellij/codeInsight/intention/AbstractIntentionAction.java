package com.intellij.codeInsight.intention;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

/**
 * @author Dmitry Avdeev
 */
public abstract class AbstractIntentionAction implements IntentionAction {

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public boolean startInWriteAction() {
    return false;
  }
}
