/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
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

  private static final Key<Class> TEMPLATE_VIEW_PROVIDER_CLASS_KEY = Key.create("TEMPLATE_VIEW_PROVIDER_CLASS");

  // this redundant ctr is here because ExtensionComponentAdapter.getComponentInstance() is not aware of varargs
  protected TemplateLanguageErrorFilter(
    @NotNull final TokenSet templateExpressionStartTokens,
    @NotNull final Class templateFileViewProviderClass)
  {
    this(templateExpressionStartTokens, templateFileViewProviderClass, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  protected TemplateLanguageErrorFilter(@NotNull final TokenSet templateExpressionStartTokens,
                                        @NotNull final Class templateFileViewProviderClass,
                                        @NotNull final String... knownSubLanguageNames) {
    myTemplateExpressionStartTokens = TokenSet.create(templateExpressionStartTokens.getTypes());
    myTemplateFileViewProviderClass = templateFileViewProviderClass;

    List<String> knownSubLanguageList = new ArrayList<>(Arrays.asList(knownSubLanguageNames));
    knownSubLanguageList.add("JavaScript");
    knownSubLanguageList.add("CSS");
    knownLanguageSet = new HashSet<>();
    for (String name : knownSubLanguageList) {
      final Language language = Language.findLanguageByID(name);
      if (language != null) {
        knownLanguageSet.add(language);
      }
    }
  }

  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    if (isKnownSubLanguage(element.getParent().getLanguage())) {
      //
      // Immediately discard filters with non-matching template class if already known
      //
      Class templateClass = element.getUserData(TEMPLATE_VIEW_PROVIDER_CLASS_KEY);
      if (templateClass != null && (templateClass != myTemplateFileViewProviderClass)) return true;

      PsiFile psiFile = element.getContainingFile();
      int offset = element.getTextOffset();
      InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(element.getProject());
      if (injectedLanguageManager.isInjectedFragment(psiFile)) {
        PsiElement host = injectedLanguageManager.getInjectionHost(element);
        if (host != null) {
          psiFile = host.getContainingFile();
          offset = injectedLanguageManager.injectedToHost(element, offset);
        }
      }
      final FileViewProvider viewProvider = psiFile.getViewProvider();
      element.putUserData(TEMPLATE_VIEW_PROVIDER_CLASS_KEY, viewProvider.getClass());
      if (!(viewProvider.getClass() == myTemplateFileViewProviderClass)) {
        return true;
      }
      //
      // An error can occur at template element or before it. Check both.
      //
      if (shouldIgnoreErrorAt(viewProvider, offset) || shouldIgnoreErrorAt(viewProvider, offset + 1)) return false;
    }
    return true;
  }

  protected boolean shouldIgnoreErrorAt(@NotNull FileViewProvider viewProvider, int offset) {
    PsiElement element = viewProvider.findElementAt(offset, viewProvider.getBaseLanguage());
    if (element instanceof PsiWhiteSpace) element = element.getNextSibling();
    if (element != null && myTemplateExpressionStartTokens.contains(element.getNode().getElementType())) {
      return true;
    }
    return false;
  }

  protected boolean isKnownSubLanguage(@NotNull final Language language) {
    for (Language knownLanguage : knownLanguageSet) {
      if (language.is(knownLanguage) || knownLanguage.getDialects().contains(language)) {
        return true;
      }
    }
    return false;
  }
}
