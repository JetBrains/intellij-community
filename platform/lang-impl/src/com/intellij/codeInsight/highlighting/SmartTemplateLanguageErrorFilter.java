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
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by hurricup on 23.06.2016.
 * This is an extended TemplateLanguageErrorFilter, providing additional method for checking errorElement
 */
public abstract class SmartTemplateLanguageErrorFilter extends HighlightErrorFilter {
  private static final Key<Class> TEMPLATE_VIEW_PROVIDER_CLASS_KEY = Key.create("TEMPLATE_VIEW_PROVIDER_CLASS");
  @NotNull
  private final TokenSet myTemplateExpressionStartTokens;
  @NotNull
  private final Class myTemplateFileViewProviderClass;
  private final Set<Language> knownLanguageSet;

  protected SmartTemplateLanguageErrorFilter(
    @NotNull final TokenSet templateExpressionStartTokens,
    @NotNull final Class templateFileViewProviderClass) {
    this(templateExpressionStartTokens, templateFileViewProviderClass, new String[0]);
  }

  protected SmartTemplateLanguageErrorFilter(
    @NotNull final TokenSet templateExpressionStartTokens,
    @NotNull final Class templateFileViewProviderClass,
    @NotNull final String... knownSubLanguageNames) {
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
    if (isKnownSubLanguage(element.getParent().getLanguage())) {
      //
      // Immediately discard filters with non-matching template class if already known
      //
      Class templateClass = element.getUserData(TEMPLATE_VIEW_PROVIDER_CLASS_KEY);
      if (templateClass != null && (templateClass != myTemplateFileViewProviderClass)) {
        return true;
      }

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
      if (shouldIgnorePrefixErrorAt(viewProvider, offset)
          || shouldIgnorePrefixErrorAt(viewProvider, offset + 1)
          || shouldIgnoreErrorElement(element)
        ) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if we should ignore an error occurred on junction of baseLanguage and Template language at specified offset
   *
   * @param viewProvider file view provider
   * @param offset       offset in question
   * @return true if we should ignore it
   */
  protected boolean shouldIgnorePrefixErrorAt(@NotNull FileViewProvider viewProvider, int offset) {
    PsiElement element = viewProvider.findElementAt(offset, viewProvider.getBaseLanguage());
    if (element instanceof PsiWhiteSpace) {
      element = element.getNextSibling();
    }
    return (element != null && myTemplateExpressionStartTokens.contains(element.getNode().getElementType()));
  }

  /**
   * Can provide additional logic for error element checking. Default implementation checks if top-level psi element on
   * the error offset is not after psi element with baseLanguage block in it
   *
   * @param errorElement error element in question
   * @return true if we should ignore it
   */
  protected boolean shouldIgnoreErrorElement(@NotNull PsiErrorElement errorElement) {
    PsiElement errorContainer = errorElement.getParent();
    if (errorContainer == null) {
      return false;
    }

    while (true) {
      PsiElement parent = errorContainer.getParent();
      if (parent == null || parent instanceof PsiFile) {
        return false;
      }
      if (parent.getNode().getStartOffset() != errorContainer.getNode().getStartOffset()) {
        break;
      }
      errorContainer = parent;
    }

    errorContainer = errorContainer.getPrevSibling();

    if (errorContainer == null) {
      return false;
    }

    if (errorContainer instanceof OuterLanguageElement) {
      return true;
    }

    // this is a wide guess, not 100% guarantee; May be we could make some more logic here;
    return PsiTreeUtil.getChildOfType(errorContainer, OuterLanguageElement.class) != null;
  }

  protected boolean isKnownSubLanguage(@NotNull final Language language) {
    for (Language knownLanguage : knownLanguageSet) {
      if (language.is(knownLanguage)) {
        return true;
      }
    }
    return false;
  }
}
