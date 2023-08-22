// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl;

import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReferenceProviderBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class ReferenceProviders {
  private static final ExtensionPointName<PsiSymbolReferenceProviderBean> EP_NAME =
    new ExtensionPointName<>("com.intellij.psi.symbolReferenceProvider");

  /**
   * Given language of a host element returns list of providers that could provide references from this language.
   */
  static @NotNull LanguageReferenceProviders byLanguage(@NotNull Language language) {
    return EP_NAME.computeIfAbsent(language, ReferenceProviders.class, ReferenceProviders::byLanguageInner);
  }

  private static @NotNull LanguageReferenceProviders byLanguageInner(@NotNull Language language) {
    List<PsiSymbolReferenceProviderBean> result = new ArrayList<>();
    for (PsiSymbolReferenceProviderBean bean : EP_NAME.getExtensionList()) {
      Language hostLanguage = bean.getHostLanguage();
      boolean matches = hostLanguage instanceof MetaLanguage ? ((MetaLanguage)hostLanguage).matchesLanguage(language)
                                                             : hostLanguage == Language.ANY || language.isKindOf(hostLanguage);
      if (matches) {
        result.add(bean);
      }
    }
    return new LanguageReferenceProviders(result);
  }

  /**
   * Given class of target returns list of providers that could provide references to this target.
   */
  public static @NotNull List<PsiSymbolReferenceProviderBean> byTargetClass(@NotNull Class<? extends Symbol> targetClass) {
    return EP_NAME.getByGroupingKey(targetClass, ReferenceProviders.class, bean -> bean.getResolveTargetClass().isAssignableFrom(targetClass) ? targetClass : null);
  }
}
