// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public final class FieldJoinLinesHandler implements JoinLinesHandlerDelegate {
  @Override
  public int tryJoinLines(@NotNull Document document, @NotNull PsiFile file, int start, int end) {
    PsiElement elementAtStartLineEnd = file.findElementAt(start);
    if (!PsiUtil.isJavaToken(elementAtStartLineEnd, JavaTokenType.SEMICOLON)) return -1;
    PsiElement elementAtNextLineStart = file.findElementAt(end);
    if (elementAtNextLineStart == null) return -1;
    PsiField firstVar = ObjectUtils.tryCast(elementAtStartLineEnd.getParent(), PsiField.class);
    if (firstVar == null) return -1;
    PsiField secondVar = PsiTreeUtil.getParentOfType(elementAtNextLineStart, PsiField.class);
    if (secondVar == null || secondVar == firstVar) return -1;
    if (PsiTreeUtil.skipWhitespacesForward(firstVar) != secondVar) return -1;
    return VariableJoinLinesHandler.joinVariables(document, elementAtStartLineEnd, firstVar, secondVar);
  }
}
