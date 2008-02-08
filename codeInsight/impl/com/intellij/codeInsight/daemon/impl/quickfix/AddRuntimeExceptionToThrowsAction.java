package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author mike
 */
public class AddRuntimeExceptionToThrowsAction implements IntentionAction {

  public boolean startInWriteAction() {
    return false;
  }

  public String getText() {
    return QuickFixBundle.message("add.runtime.exception.to.throws.text");
  }

  public void invoke(final Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiClassType aClass = getRuntimeExceptionAtCaret(editor, file);
    PsiMethod method = PsiTreeUtil.getParentOfType(elementAtCaret(editor, file), PsiMethod.class);

    AddExceptionToThrowsFix.addExceptionsToThrowsList(project, method, aClass);
  }


  private static boolean isMethodThrows(PsiMethod method, PsiClassType exception) {
    PsiClassType[] throwsTypes = method.getThrowsList().getReferencedTypes();
    for (PsiClassType throwsType : throwsTypes) {
      if (throwsType.isAssignableFrom(exception)) {
        return true;
      }
    }
    return false;
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    PsiClassType exception = getRuntimeExceptionAtCaret(editor, file);
    if (exception == null) return false;

    PsiMethod method = PsiTreeUtil.getParentOfType(elementAtCaret(editor, file), PsiMethod.class);
    if (method == null || !method.getThrowsList().isPhysical()) return false;

    return !isMethodThrows(method, exception);
  }

  private static PsiClassType getRuntimeExceptionAtCaret(Editor editor, PsiFile file) {
    PsiElement element = elementAtCaret(editor, file);
    if (element == null) return null;
    PsiThrowStatement expression = PsiTreeUtil.getParentOfType(element, PsiThrowStatement.class);
    if (expression == null) return null;
    PsiExpression exception = expression.getException();
    if (exception == null) return null;
    PsiType type = exception.getType();
    if (!(type instanceof PsiClassType)) return null;
    if (!ExceptionUtil.isUncheckedException((PsiClassType)type)) return null;
    return (PsiClassType)type;
  }

  private static PsiElement elementAtCaret(final Editor editor, final PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    return element;
  }


  public String getFamilyName() {
    return QuickFixBundle.message("add.runtime.exception.to.throws.family");
  }
}
