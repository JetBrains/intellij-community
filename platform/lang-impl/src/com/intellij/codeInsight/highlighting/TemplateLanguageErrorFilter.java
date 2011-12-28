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
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public abstract class TemplateLanguageErrorFilter extends HighlightErrorFilter {
  private final IElementType myTemplateExpressionStart;
  private final Class myTemplateFileViewProviderClass;

  protected TemplateLanguageErrorFilter(IElementType templateExpressionStart, Class templateFileViewProviderClass) {
    myTemplateExpressionStart = templateExpressionStart;
    myTemplateFileViewProviderClass = templateFileViewProviderClass;
  }

  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    final FileViewProvider viewProvider = element.getContainingFile().getViewProvider();
    if (!(viewProvider.getClass() == myTemplateFileViewProviderClass)) {
      return true;
    }
    final Language parentLanguage = element.getParent().getLanguage();
    final Language javaScript = Language.findLanguageByID("JavaScript");
    final Language css = Language.findLanguageByID("CSS");
    if (javaScript != null && parentLanguage.is(javaScript) || css != null && parentLanguage.is(css)) {
      final PsiElement next = viewProvider.findElementAt(element.getTextOffset() + 1, viewProvider.getBaseLanguage());
      if (next != null && next.getNode().getElementType() == myTemplateExpressionStart) {
        return false;
      }
    }
    return true;
  }
}
