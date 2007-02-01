package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class AddOverrideAnnotationAction implements IntentionAction {
  private static final String ourFQName = "java.lang.Override";

  public String getText() {
    return CodeInsightBundle.message("intention.add.override.annotation");
  }

  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.override.annotation.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(file)) > 0) return false;
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    if (method == null) return false;
    if (method.getModifierList().findAnnotation(ourFQName) != null) return false;
    PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      if (!superMethod.hasModifierProperty(PsiModifier.ABSTRACT)
          && new AddAnnotationFix(ourFQName, method).isAvailable(project, editor, file)) {
        return true;
      }
    }

    return false;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    new AddAnnotationFix(ourFQName, method).invoke(project, editor, file);
  }

  private static PsiMethod findMethod(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    PsiMethod res = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (res == null) return null;

    //Not available in method's body
    PsiCodeBlock body = res.getBody();
    if (body == null) return null;
    if (body.getTextRange().getStartOffset() <= offset) return null;

    return res;
  }

  public boolean startInWriteAction() {
    return true;
  }
}
