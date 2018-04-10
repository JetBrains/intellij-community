/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceGetClassWithClassLiteralFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  private String myText;

  public ReplaceGetClassWithClassLiteralFix(PsiMethodCallExpression expression) {
    super(expression);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiClass aClass = PsiTreeUtil.getParentOfType(startElement, PsiClass.class);
    assert aClass != null;
    PsiExpression classLiteral = JavaPsiFacade.getElementFactory(project).createExpressionFromText(aClass.getName() + ".class", startElement);
    new CommentTracker().replaceAndRestoreComments(startElement, classLiteral);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    PsiClass aClass = PsiTreeUtil.getParentOfType(startElement, PsiClass.class);
    if (aClass == null) return false;
    String className = aClass.getName();
    if (className == null) return false;
    myText = "Replace with " + className + ".class";
    return super.isAvailable(project, file, startElement, endElement);
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace getClass() with .class literal";
  }

  public static void registerFix(PsiMethodCallExpression callExpression, HighlightInfo errorResult) {
    if (callExpression.getMethodExpression().getQualifierExpression() == null) {
      PsiMethod method = callExpression.resolveMethod();
      if (method != null && PsiTypesUtil.isGetClass(method)) {
        QuickFixAction.registerQuickFixAction(errorResult, new ReplaceGetClassWithClassLiteralFix(callExpression));
      }
    }
  }
}
