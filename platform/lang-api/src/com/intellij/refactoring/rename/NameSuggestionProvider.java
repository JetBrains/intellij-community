// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.rename;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author yole
 */
public interface NameSuggestionProvider {
  ExtensionPointName<NameSuggestionProvider> EP_NAME = ExtensionPointName.create("com.intellij.nameSuggestionProvider");

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
