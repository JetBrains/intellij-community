package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CopyAbstractMethodImplementationAction extends ImplementAbstractMethodAction {
  @NotNull
  public String getFamilyName() {
    return "Copy Abstract Method Implementation";
  }

  protected String getIntentionName(final PsiMethod method) {
    return CodeInsightBundle.message("copy.abstract.method.intention.name", method.getName());
  }

  protected boolean isAvailable(final MyElementProcessor processor) {
    return processor.hasMissingImplementations() && processor.hasExistingImplementations();
  }

  protected void invokeHandler(final Project project, final Editor editor, final PsiMethod method) {
    new CopyAbstractMethodImplementationHandler(project, editor, method).invoke();
  }
}
