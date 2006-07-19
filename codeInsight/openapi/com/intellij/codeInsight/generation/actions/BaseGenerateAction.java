package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

public class BaseGenerateAction extends CodeInsightAction {
  private CodeInsightActionHandler myHandler;

  public BaseGenerateAction(CodeInsightActionHandler handler) {
    myHandler = handler;
  }

  protected final CodeInsightActionHandler getHandler() {
    return myHandler;
  }

  protected PsiClass getTargetClass (Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    return PsiTreeUtil.getParentOfType(element, PsiClass.class);
  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    if (file instanceof PsiCompiledElement) return false;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiClass targetClass = getTargetClass(editor, file);
    if (targetClass == null) return false;
    if (targetClass.isInterface()) return false; //?

    return true;
  }
}