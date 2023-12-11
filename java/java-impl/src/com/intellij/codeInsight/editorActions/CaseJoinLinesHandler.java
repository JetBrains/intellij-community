// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public final class CaseJoinLinesHandler implements JoinLinesHandlerDelegate {
  @Override
  public int tryJoinLines(@NotNull final Document document, @NotNull final PsiFile psiFile, final int start, final int end) {
    if (!HighlightingFeature.ENHANCED_SWITCH.isAvailable(psiFile)) return -1;
    PsiElement elementAtStartLineEnd = psiFile.findElementAt(start);
    PsiElement elementAtNextLineStart = psiFile.findElementAt(end);
    if (elementAtStartLineEnd == null || elementAtNextLineStart == null) return -1;
    if (!PsiUtil.isJavaToken(elementAtStartLineEnd, JavaTokenType.COLON)) return -1;
    if (!(elementAtNextLineStart instanceof PsiKeyword) ||
        ((PsiKeyword)elementAtNextLineStart).getTokenType() != JavaTokenType.CASE_KEYWORD) {
      return -1;
    }
    PsiSwitchLabelStatement firstLabel = PsiTreeUtil.getParentOfType(elementAtStartLineEnd, PsiSwitchLabelStatement.class);
    if (firstLabel == null || firstLabel.isDefaultCase()) return -1;
    PsiSwitchLabelStatement secondLabel = PsiTreeUtil.getParentOfType(elementAtNextLineStart, PsiSwitchLabelStatement.class);
    if (secondLabel == null || secondLabel == firstLabel) return -1;
    PsiElement nextToken = PsiTreeUtil.skipWhitespacesForward(elementAtNextLineStart);
    if (nextToken == null) return -1;
    int replaceStart = elementAtStartLineEnd.getTextRange().getStartOffset();
    int replaceEnd = nextToken.getTextRange().getStartOffset();
    boolean spaceAfterComma = CodeStyle.getSettings(psiFile).getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_COMMA;
    document.replaceString(replaceStart, replaceEnd, "," + (spaceAfterComma ? " " : ""));
    return replaceStart;
  }
}
