// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SemicolonFixer extends AbstractBasicSemicolonFixer {
  @Override
  protected boolean fixReturn(@NotNull Editor editor, @Nullable PsiElement psiElement) {
    if (psiElement instanceof PsiReturnStatement stmt) {
      PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class, true, PsiLambdaExpression.class);
      if (method != null && PsiTypes.voidType().equals(method.getReturnType()) && stmt.getReturnValue() != null) {
        Document doc = editor.getDocument();
        doc.insertString(stmt.getTextRange().getStartOffset() + "return".length(), ";");
        return true;
      }
    }
    return false;
  }

  @Override
  protected boolean getSpaceAfterSemicolon(@NotNull PsiElement psiElement) {
    return CodeStyle.getSettings(psiElement.getContainingFile()).getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_SEMICOLON;
  }

  @Override
  protected boolean isImportStatementBase(@Nullable PsiElement psiElement) {
    return psiElement instanceof PsiImportStatementBase;
  }
}