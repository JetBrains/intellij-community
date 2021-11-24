// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.rename;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * An extension point to generate language dependent name suggestions.
 */
public interface NameSuggestionProvider {
  ExtensionPointName<NameSuggestionProvider> EP_NAME = ExtensionPointName.create("com.intellij.nameSuggestionProvider");

  /**
   * Provides possible names used as suggestions when renaming {@code element}.
   * @param element                 the element which would be renamed.
   * @param nameSuggestionContext   the context element.
   * @param result                  set to store all name variants.
   *                                
   * @return                        {@code null} if provider is not applicable to the {@code element}. 
   *                                When non-null {@link SuggestedNameInfo} is returned, it's {@link SuggestedNameInfo#nameChosen(String)} would be called when rename is performed.
   *                                Can be used e.g., to track name related statistics.
   */
  @Nullable
  SuggestedNameInfo getSuggestedNames(@NotNull PsiElement element, @Nullable PsiElement nameSuggestionContext, @NotNull Set<String> result);

  static SuggestedNameInfo suggestNames(PsiElement psiElement, PsiElement nameSuggestionContext, Set<String> result) {
    SuggestedNameInfo resultInfo = null;
    for (NameSuggestionProvider provider : EP_NAME.getExtensionList()) {
      SuggestedNameInfo info = provider.getSuggestedNames(psiElement, nameSuggestionContext, result);
      if (info != null) {
        resultInfo = info;
        if (provider instanceof PreferrableNameSuggestionProvider && !((PreferrableNameSuggestionProvider)provider).shouldCheckOthers()) {
          break;
        }
      }
    }
    return resultInfo;
  }
}
