package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

/**
 * @author yole
 */
public class JavaImplementMethodsHandler implements LanguageCodeInsightActionHandler {
  public boolean isValidFor(final Editor editor, final PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }

    PsiClass aClass = OverrideImplementUtil.getContextClass(file.getProject(), editor, file, false);
    return aClass != null;
  }

  public void invoke(final Project project, final Editor editor, final PsiFile file) {
    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
    if (aClass == null) {
      return;
    }
    if (OverrideImplementUtil.getMethodSignaturesToImplement(aClass).isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, "No methods to implement have been found");
      return;
    }
    OverrideImplementUtil.chooseAndImplementMethods(project, editor, aClass);
  }

  public boolean startInWriteAction() {
    return false;
  }
}
