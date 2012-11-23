/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.highlighting;

import com.intellij.lang.Language;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public abstract class TemplateLanguageErrorFilter extends HighlightErrorFilter {
  @NotNull
  private final TokenSet myTemplateExpressionStartTokens;
  @NotNull
  private final Class myTemplateFileViewProviderClass;

  private final Set<Language> knownLanguageSet;

  protected TemplateLanguageErrorFilter(
    final @NotNull TokenSet templateExpressionStartTokens,
    final @NotNull Class templateFileViewProviderClass)
  {
    this(templateExpressionStartTokens, templateFileViewProviderClass, new String[0]);
  }

  protected TemplateLanguageErrorFilter(
    final @NotNull TokenSet templateExpressionStartTokens,
    final @NotNull Class templateFileViewProviderClass,
    final @NotNull String... knownSubLanguageNames)
  {
    myTemplateExpressionStartTokens = TokenSet.create(templateExpressionStartTokens.getTypes());
    myTemplateFileViewProviderClass = templateFileViewProviderClass;

    List<String> knownSubLanguageList = new ArrayList<String>(Arrays.asList(knownSubLanguageNames));
    knownSubLanguageList.add("JavaScript");
    knownSubLanguageList.add("CSS");
    knownLanguageSet = new HashSet<Language>();
    for (String name : knownSubLanguageList) {
      final Language language = Language.findLanguageByID(name);
      if (language != null) {
        knownLanguageSet.add(language);
      }
    }
  }

  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    final FileViewProvider viewProvider = element.getContainingFile().getViewProvider();
    if (!(viewProvider.getClass() == myTemplateFileViewProviderClass)) {
      return true;
    }
    if (isKnownSubLanguage(element.getParent().getLanguage())) {
      final PsiElement next = viewProvider.findElementAt(element.getTextOffset() + 1, viewProvider.getBaseLanguage());
      if (next != null && myTemplateExpressionStartTokens.contains(next.getNode().getElementType())) {
        return false;
      }
    }
    return true;
  }

  protected boolean isKnownSubLanguage(final @NotNull Language language) {
    for (Language knownLanguage : knownLanguageSet) {
      if (language.is(knownLanguage)) {
        return true;
      }
    }
    return false;
  }
}
