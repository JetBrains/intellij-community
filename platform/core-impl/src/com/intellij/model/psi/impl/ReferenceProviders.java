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
  private final Map<Class<? extends Symbol>, List<PsiSymbolReferenceProviderBean>> myByTargetClass = new ConcurrentHashMap<>();

  public ReferenceProviders() {
    EP_NAME.addExtensionPointListener(this::clearCaches, ApplicationManager.getApplication());
  }

  public void clearCaches() {
    myByHostLanguage.clear();
    myByTargetClass.clear();
  }

  @NotNull
  public static ReferenceProviders getInstance() {
    return ServiceManager.getService(ReferenceProviders.class);
  }

  /**
   * Given language of a host element returns list of providers that could provide references from this language.
   */
  @NotNull
  LanguageReferenceProviders byLanguage(@NotNull Language language) {
    return myByHostLanguage.computeIfAbsent(language, ReferenceProviders::createLanguageProviders);
  }

  @NotNull
  private static LanguageReferenceProviders createLanguageProviders(@NotNull Language language) {
    return new LanguageReferenceProviders(byLanguageInner(language));
  }

  @NotNull
  private static List<PsiSymbolReferenceProviderBean> byLanguageInner(@NotNull Language language) {
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
  @NotNull
  public List<PsiSymbolReferenceProviderBean> byTargetClass(@NotNull Class<? extends Symbol> targetClass) {
    return myByTargetClass.computeIfAbsent(targetClass, ReferenceProviders::byTargetClassInner);
  }

  @NotNull
  private static List<PsiSymbolReferenceProviderBean> byTargetClassInner(@NotNull Class<? extends Symbol> targetClass) {
    List<PsiSymbolReferenceProviderBean> result = new ArrayList<>();
    for (PsiSymbolReferenceProviderBean bean : EP_NAME.getExtensionList()) {
      if (bean.getResolveTargetClass().isAssignableFrom(targetClass)) {
        result.add(bean);
      }
    }
    return result;
  }
}
