// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DefaultHighlightUtil {
  /**
   * @deprecated use {@link #checkUnicodeBadCharacter(PsiElement)} instead
   */
  @Deprecated
  @Nullable
  public static HighlightInfo checkBadCharacter(@NotNull PsiElement element) {
    HighlightInfo.Builder builder = checkUnicodeBadCharacter(element);
    return builder == null ? null : builder.create();
  }
  @Nullable
  public static HighlightInfo.Builder checkUnicodeBadCharacter(@NotNull PsiElement element) {
    ASTNode node = element.getNode();
    if (node != null && node.getElementType() == TokenType.BAD_CHARACTER) {
      char c = element.textToCharArray()[0];
      boolean printable = StringUtil.isPrintableUnicode(c) && !Character.isSpaceChar(c);
      @NlsSafe String hex = String.format("U+%04X", (int)c);
      String text = AnalysisBundle.message("text.illegal.character", printable ? c + " (" + hex + ")" : hex);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(text);
    }

    return null;
  }
}