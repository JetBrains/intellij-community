// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl;

import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.model.psi.PsiSymbolReferenceProvider;
import com.intellij.model.psi.PsiSymbolReferenceProviderBean;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds reference providers for single language.
 */
final class LanguageReferenceProviders {

  private final List<PsiSymbolReferenceProviderBean> myBeans;
  private final Map<Class<?>, List<PsiSymbolReferenceProviderBean>> myBeansByHostClass = new ConcurrentHashMap<>();

  LanguageReferenceProviders(@NotNull List<PsiSymbolReferenceProviderBean> beans) {
    myBeans = beans;
  }

  @NotNull
  List<PsiSymbolReferenceProvider> getProviders(@NotNull PsiExternalReferenceHost element) {
    return ContainerUtil.map(byHostClass(element), PsiSymbolReferenceProviderBean::getInstance);
  }

  @NotNull
  private List<PsiSymbolReferenceProviderBean> byHostClass(@NotNull PsiExternalReferenceHost element) {
    return myBeansByHostClass.computeIfAbsent(element.getClass(), this::byHostClassInner);
  }

  @NotNull
  private List<PsiSymbolReferenceProviderBean> byHostClassInner(@NotNull Class<?> key) {
    List<PsiSymbolReferenceProviderBean> result = new SmartList<>();
    for (PsiSymbolReferenceProviderBean bean : myBeans) {
      if (bean.getHostElementClass().isAssignableFrom(key)) {
        result.add(bean);
      }
    }
    return result;
  }
}
