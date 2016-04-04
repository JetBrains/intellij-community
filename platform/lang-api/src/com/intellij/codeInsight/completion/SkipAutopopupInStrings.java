/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class SkipAutopopupInStrings extends CompletionConfidence {

  @NotNull
  @Override
  public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    if (isInStringLiteral(contextElement)) {
      return ThreeState.YES;
    }

    return ThreeState.UNSURE;
  }

  public static boolean isInStringLiteral(PsiElement element) {
    ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(PsiUtilCore.findLanguageFromElement(element));
    if (definition == null) {
      return false;
    }

    return isStringLiteral(element, definition) || isStringLiteral(element.getParent(), definition) ||
            isStringLiteralWithError(element, definition) || isStringLiteralWithError(element.getParent(), definition);
  }

  private static boolean isStringLiteral(PsiElement element, ParserDefinition definition) {
    return PlatformPatterns.psiElement().withElementType(definition.getStringLiteralElements()).accepts(element);
  }

  private static boolean isStringLiteralWithError(PsiElement element, ParserDefinition definition) {
    return isStringLiteral(element, definition) && PsiTreeUtil.nextLeaf(element) instanceof PsiErrorElement;
  }
}
