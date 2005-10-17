package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NonNls;

/**
 *
 */
public class GenerateSuperMethodCallAction extends BaseCodeInsightAction {
  protected CodeInsightActionHandler getHandler() {
    return new GenerateSuperMethodCallHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    if (!file.canContainJavaCode()) {
      return false;
    }
    PsiMethod method = GenerateSuperMethodCallHandler.canInsertSuper(project, editor, file);
    if (method == null) {
      return false;
    }
    final @NonNls String text = "super." + method.getName() + "()";
    getTemplatePresentation().setText(text);
    return true;
  }
}