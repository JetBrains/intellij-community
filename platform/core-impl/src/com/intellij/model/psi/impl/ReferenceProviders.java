// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl;

import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReferenceProviderBean;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jetbrains.annotations.ApiStatus.Internal;

@Internal
@Service
public final class ReferenceProviders {
  private static final ExtensionPointName<PsiSymbolReferenceProviderBean> EP_NAME = new ExtensionPointName<>(
    "com.intellij.psi.symbolReferenceProvider"
  );

  private final Map<Language, LanguageReferenceProviders> myByHostLanguage = new ConcurrentHashMap<>();

  public ReferenceProviders() {
    EP_NAME.addExtensionPointListener(this::clearCaches, ApplicationManager.getApplication());
  }

  public void clearCaches() {
    myByHostLanguage.clear();
  }

  public static @NotNull ReferenceProviders getInstance() {
    return ServiceManager.getService(ReferenceProviders.class);
  }

  /**
   * Given language of a host element returns list of providers that could provide references from this language.
   */
  @NotNull
  LanguageReferenceProviders byLanguage(@NotNull Language language) {
    return myByHostLanguage.computeIfAbsent(language, ReferenceProviders::createLanguageProviders);
  }

  private static @NotNull LanguageReferenceProviders createLanguageProviders(@NotNull Language language) {
    return new LanguageReferenceProviders(byLanguageInner(language));
  }

  private static @NotNull List<PsiSymbolReferenceProviderBean> byLanguageInner(@NotNull Language language) {
    List<PsiSymbolReferenceProviderBean> result = new ArrayList<>();
    for (PsiSymbolReferenceProviderBean bean : EP_NAME.getExtensionList()) {
      Language hostLanguage = bean.getHostLanguage();
      boolean matches = hostLanguage instanceof MetaLanguage ? ((MetaLanguage)hostLanguage).matchesLanguage(language)
                                                             : language.isKindOf(hostLanguage);
      if (matches) {
        result.add(bean);
      }
    }
    return result;
  }

  /**
   * Given class of target returns list of providers that could provide references to this target.
   */
  public @NotNull static List<PsiSymbolReferenceProviderBean> byTargetClass(@NotNull Class<? extends Symbol> targetClass) {
    return EP_NAME.getByGroupingKey(targetClass, bean -> bean.getResolveTargetClass().isAssignableFrom(targetClass) ? targetClass : null);
  }
}
