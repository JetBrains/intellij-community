package com.intellij.codeInsight.generation;

import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavaOverrideMethodsHandler implements LanguageCodeInsightActionHandler {
  public boolean isValidFor(final Editor editor, final PsiFile file) {
    return file instanceof PsiJavaFile && OverrideImplementUtil.getContextClass(file.getProject(), editor, file, true) != null;
  }

  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, true);
    if (aClass != null) {
      OverrideImplementUtil.chooseAndOverrideMethods(project, editor, aClass);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}
