// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl;

import com.intellij.model.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ReferenceRange;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Internal
final class PsiSymbolReferenceServiceImpl implements PsiSymbolReferenceService {
  /**
   * This field is intentionally private.
   * Clients are supposed to use {@link #getReferences(PsiElement)} to obtain all available references.
   */
  private static final PsiSymbolReferenceHints EMPTY_HINTS = new PsiSymbolReferenceHints() {
  };

  @Override
  public @NotNull Iterable<? extends PsiSymbolReference> getReferences(@NotNull PsiElement element) {
    return CachedValuesManager.getCachedValue(element, () -> CachedValueProvider.Result.create(
      Collections.unmodifiableList(getReferences(element, EMPTY_HINTS)), PsiModificationTracker.MODIFICATION_COUNT)
    );
  }

  @SuppressWarnings("unchecked")
  @Override
  public @NotNull <T extends PsiSymbolReference> Collection<T> getReferences(@NotNull PsiElement host, @NotNull Class<T> referenceClass) {
    return (Collection<T>)getReferences(host, PsiSymbolReferenceHints.referenceClassHint(referenceClass));
  }

  @Override
  public @NotNull List<PsiSymbolReference> getReferences(@NotNull PsiElement element, @NotNull PsiSymbolReferenceHints hints) {
    List<PsiSymbolReference> result = ContainerUtil.newArrayList(element.getOwnReferences());
    if (result.isEmpty() && element instanceof PsiExternalReferenceHost) {
      result.addAll(doGetExternalReferences((PsiExternalReferenceHost)element, hints));
    }
    return applyHints(result, hints);
  }

  @Override
  public @NotNull Collection<? extends PsiSymbolReference> getExternalReferences(@NotNull PsiExternalReferenceHost element,
                                                                                 @NotNull PsiSymbolReferenceHints hints) {
    return applyHints(doGetExternalReferences(element, hints), hints);
  }

  private static @NotNull List<PsiSymbolReference> doGetExternalReferences(@NotNull PsiExternalReferenceHost element,
                                                                           @NotNull PsiSymbolReferenceHints hints) {
    List<PsiSymbolReferenceProviderBean> beans = ReferenceProviders.byLanguage(element.getLanguage()).byHostClass(element.getClass());
    if (beans.isEmpty()) {
      return Collections.emptyList();
    }
    Class<? extends PsiSymbolReference> requiredReferenceClass = hints.getReferenceClass();
    List<PsiSymbolReference> result = new SmartList<>();
    for (PsiSymbolReferenceProviderBean bean : beans) {
      if (requiredReferenceClass == PsiSymbolReference.class // top required
          || bean.anyReferenceClass // bottom provided
          || requiredReferenceClass.isAssignableFrom(bean.getReferenceClass())) {
        result.addAll(bean.getInstance().getReferences(element, hints));
      }
    }
    return result;
  }

  private static @NotNull List<PsiSymbolReference> applyHints(@NotNull List<PsiSymbolReference> references,
                                                              @NotNull PsiSymbolReferenceHints hints) {
    if (hints == EMPTY_HINTS) {
      return references;
    }
    List<PsiSymbolReference> result = references;

    Class<? extends PsiSymbolReference> referenceClass = hints.getReferenceClass();
    if (referenceClass != PsiSymbolReference.class) {
      result = ContainerUtil.filterIsInstance(result, referenceClass);
    }

    Integer offsetInElement = hints.getOffsetInElement();
    if (offsetInElement != null) {
      result = ContainerUtil.filter(result, it -> ReferenceRange.containsOffsetInElement(it, offsetInElement));
    }
    // consider checking SymbolReference.resolvesTo(target) here if all needed
    return result;
  }
}
