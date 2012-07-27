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

/**
 * @author Dennis.Ushakov
 */
public abstract class TemplateLanguageErrorFilter extends HighlightErrorFilter {
  @NotNull
  private final TokenSet myTemplateExpressionStartTokens;
  @NotNull
  private final Class myTemplateFileViewProviderClass;

  protected TemplateLanguageErrorFilter(
    final @NotNull TokenSet templateExpressionStartTokens,
    final @NotNull Class templateFileViewProviderClass)
  {
    myTemplateExpressionStartTokens = TokenSet.create(templateExpressionStartTokens.getTypes());
    myTemplateFileViewProviderClass = templateFileViewProviderClass;
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
    final Language javaScript = Language.findLanguageByID("JavaScript");
    final Language css = Language.findLanguageByID("CSS");
    return javaScript != null && language.is(javaScript) || css != null && language.is(css);
  }
}
