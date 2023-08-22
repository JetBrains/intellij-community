// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class AddRuntimeExceptionToThrowsAction implements IntentionAction {

  private String myThrowsClause;

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("add.runtime.exception.to.throws.text", myThrowsClause);
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) {
    PsiClassType aClass = getRuntimeExceptionAtCaret(editor, file);
    PsiMethod method = PsiTreeUtil.getParentOfType(elementAtCaret(editor, file), PsiMethod.class);
    if (method != null) {
      if (method.isPhysical()) {
        AddExceptionToThrowsFix.addExceptionsToThrowsList(project, method, Collections.singleton(aClass));
      } else {
        AddExceptionToThrowsFix.processMethod(project, method, Collections.singleton(aClass));
      }
    }
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

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    PsiClassType exception = getRuntimeExceptionAtCaret(editor, file);
    if (exception == null) return false;

    PsiMethod method = PsiTreeUtil.getParentOfType(elementAtCaret(editor, file), PsiMethod.class, true, PsiLambdaExpression.class);
    if (method == null || !method.getThrowsList().isPhysical() || isMethodThrows(method, exception)) return false;

    myThrowsClause = "throws " + exception.getPresentableText();
    return true;
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
    return file.findElementAt(offset);
  }


  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.runtime.exception.to.throws.family");
  }
}
