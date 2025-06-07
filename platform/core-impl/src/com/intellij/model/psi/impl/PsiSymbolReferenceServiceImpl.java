// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl;

import com.intellij.model.psi.*;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Internal
final class PsiSymbolReferenceServiceImpl implements PsiSymbolReferenceService {
  /**
   * This field is intentionally private.
   * Clients are supposed to use {@link #getReferences(PsiElement)} to obtain all available references.
   */
  private static final PsiSymbolReferenceHints EMPTY_HINTS = new PsiSymbolReferenceHints() {
  };

  @Override
  public @NotNull Collection<? extends @NotNull PsiSymbolReference> getReferences(@NotNull PsiElement element) {
    return getReferences(element, EMPTY_HINTS);
  }

  @SuppressWarnings("unchecked")
  @Override
  public @NotNull <T extends @NotNull PsiSymbolReference> Collection<T> getReferences(@NotNull PsiElement host, @NotNull Class<T> referenceClass) {
    return (Collection<T>)getReferences(host, PsiSymbolReferenceHints.referenceClassHint(referenceClass));
  }

  @Override
  public @NotNull List<@NotNull PsiSymbolReference> getReferences(@NotNull PsiElement element, @NotNull PsiSymbolReferenceHints hints) {
    List<PsiSymbolReference> result = new ArrayList<>(doGetOwnReferences(element));
    if (result.isEmpty() && element instanceof PsiExternalReferenceHost) {
      result.addAll(doGetExternalReferences((PsiExternalReferenceHost)element, hints));
    }
    return Collections.unmodifiableList(applyHints(result, hints));
  }

  @Override
  public @NotNull Collection<? extends PsiSymbolReference> getExternalReferences(@NotNull PsiExternalReferenceHost element,
                                                                                 @NotNull PsiSymbolReferenceHints hints) {
    return applyHints(doGetExternalReferences(element, hints), hints);
  }

  private static final Key<ParameterizedCachedValue<Collection<? extends PsiSymbolReference>, PsiElement>> OWN_REFERENCES_KEY =
    Key.create("PsiSymbolReferenceService.OWN_REFERENCES");

  private static final ParameterizedCachedValueProvider<Collection<? extends PsiSymbolReference>, PsiElement> OWN_REFERENCES_PROVIDER =
    element -> {
      Collection<? extends @NotNull PsiSymbolReference> references = element.getOwnReferences();
      return Result.create(references, PsiModificationTracker.MODIFICATION_COUNT);
    };

  private static @NotNull Collection<? extends PsiSymbolReference> doGetOwnReferences(@NotNull PsiElement element) {
    CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(element.getProject());
    return cachedValuesManager.getParameterizedCachedValue(element, OWN_REFERENCES_KEY, OWN_REFERENCES_PROVIDER, false, element);
  }

  private static @NotNull List<PsiSymbolReference> doGetExternalReferences(@NotNull PsiExternalReferenceHost element,
                                                                           @NotNull PsiSymbolReferenceHints hints) {
    Supplier<List<PsiSymbolReference>> supplier = () -> {
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
      return Collections.unmodifiableList(result);
    };

    if (hints == EMPTY_HINTS) {
      return CachedValuesManager.getCachedValue(element, () -> Result.create(
        supplier.get(), PsiModificationTracker.MODIFICATION_COUNT
      ));
    }

    return supplier.get();
  }

  private static @NotNull List<@NotNull PsiSymbolReference> applyHints(@NotNull List<PsiSymbolReference> references,
                                                                       @NotNull PsiSymbolReferenceHints hints) {
    if (hints == EMPTY_HINTS || references.isEmpty()) {
      return references;
    }
    List<@NotNull PsiSymbolReference> result = references;

    Class<? extends PsiSymbolReference> referenceClass = hints.getReferenceClass();
    if (referenceClass != PsiSymbolReference.class) {
      result = ContainerUtil.filterIsInstance(result, referenceClass);
    }

    int offsetInElement = hints.getOffsetInElement();
    if (offsetInElement >= 0) {
      result = ContainerUtil.filter(result, reference -> reference.getRangeInElement().containsOffset(offsetInElement));
    }
    // consider checking SymbolReference.resolvesTo(target) here if all needed
    return result;
  }
}
