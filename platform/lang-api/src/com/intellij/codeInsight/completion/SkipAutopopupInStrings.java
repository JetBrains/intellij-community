// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public class SkipAutopopupInStrings extends CompletionConfidence {

  @Override
  public @NotNull ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    if (isInStringLiteral(contextElement)) {
      return ThreeState.YES;
    }

    return ThreeState.UNSURE;
  }

  public static boolean isInStringLiteral(PsiElement element) {
    ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(PsiUtilCore.findLanguageFromElement(element));
    return definition != null && (isStringLiteral(element, definition) || isStringLiteral(element.getParent(), definition));
  }

  private static boolean isStringLiteral(PsiElement element, ParserDefinition definition) {
    return PlatformPatterns.psiElement().withElementType(definition.getStringLiteralElements()).accepts(element);
  }
}
