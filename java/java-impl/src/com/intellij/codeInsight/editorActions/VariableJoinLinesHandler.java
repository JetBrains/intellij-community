// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public final class VariableJoinLinesHandler implements JoinLinesHandlerDelegate {
  @Override
  public int tryJoinLines(@NotNull Document document, @NotNull PsiFile file, int start, int end) {
    PsiElement elementAtStartLineEnd = file.findElementAt(start);
    if (!PsiUtil.isJavaToken(elementAtStartLineEnd, JavaTokenType.SEMICOLON)) return -1;
    PsiElement elementAtNextLineStart = file.findElementAt(end);
    if (elementAtNextLineStart == null) return -1;
    PsiLocalVariable firstVar = ObjectUtils.tryCast(elementAtStartLineEnd.getParent(), PsiLocalVariable.class);
    if (firstVar == null) return -1;
    PsiDeclarationStatement firstDeclaration = ObjectUtils.tryCast(firstVar.getParent(), PsiDeclarationStatement.class);
    if (firstDeclaration == null) return -1;
    PsiLocalVariable leftMostVar = (PsiLocalVariable)firstDeclaration.getDeclaredElements()[0];
    PsiLocalVariable secondVar = PsiTreeUtil.getParentOfType(elementAtNextLineStart, PsiLocalVariable.class);
    if (secondVar == null || secondVar == firstVar) return -1;
    PsiDeclarationStatement secondDeclaration = ObjectUtils.tryCast(secondVar.getParent(), PsiDeclarationStatement.class);
    if (secondDeclaration == null) return -1;
    if (PsiTreeUtil.skipWhitespacesForward(firstDeclaration) != secondDeclaration) return -1;
    return joinVariables(document, elementAtStartLineEnd, leftMostVar, secondVar);
  }

  static int joinVariables(@NotNull Document document,
                           @NotNull PsiElement elementAtStartLineEnd,
                           @NotNull PsiVariable firstVar,
                           @NotNull PsiVariable secondVar) {
    PsiTypeElement firstTypeElement = firstVar.getTypeElement();
    if (firstTypeElement == null || firstTypeElement.isInferredType()) return -1;
    PsiTypeElement secondTypeElement = secondVar.getTypeElement();
    if (secondTypeElement == null || secondTypeElement.isInferredType()) return -1;
    if (!PsiEquivalenceUtil.areElementsEquivalent(firstTypeElement, secondTypeElement)) return -1;
    PsiModifierList firstModifiers = firstVar.getModifierList();
    PsiModifierList secondModifiers = secondVar.getModifierList();
    if (firstModifiers == null) {
      if (secondModifiers != null) return -1;
    }
    else {
      if (secondModifiers == null || !PsiEquivalenceUtil.areElementsEquivalent(firstModifiers, secondModifiers)) return -1;
    }

    int startOffset = elementAtStartLineEnd.getTextRange().getStartOffset();
    int endOffset = secondTypeElement.getTextRange().getEndOffset();
    document.replaceString(startOffset, endOffset, ",");
    return startOffset;
  }
}
