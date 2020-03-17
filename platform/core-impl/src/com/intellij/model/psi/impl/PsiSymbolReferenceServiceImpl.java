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
public final class PsiSymbolReferenceServiceImpl implements PsiSymbolReferenceService {

  /**
   * This field is intentionally private.
   * Clients are supposed to use {@link #getReferences(PsiElement)} to obtain all available references.
   */
  private static final PsiSymbolReferenceHints EMPTY_HINTS = new PsiSymbolReferenceHints() {
  };

  @NotNull
  @Override
  public Iterable<? extends PsiSymbolReference> getReferences(@NotNull PsiElement element) {
    return CachedValuesManager.getCachedValue(element, () -> CachedValueProvider.Result.create(
      Collections.unmodifiableList(getReferences(element, EMPTY_HINTS)), PsiModificationTracker.MODIFICATION_COUNT)
    );
  }

  @NotNull
  @Override
  public List<PsiSymbolReference> getReferences(@NotNull PsiElement element, @NotNull PsiSymbolReferenceHints hints) {
    List<PsiSymbolReference> result = ContainerUtil.newArrayList(element.getOwnReferences());
    if (element instanceof PsiExternalReferenceHost) {
      result.addAll(getExternalReferences((PsiExternalReferenceHost)element, hints));
    }
    return applyHints(result, hints);
  }

  @NotNull
  private static Collection<? extends PsiSymbolReference> getExternalReferences(@NotNull PsiExternalReferenceHost element,
                                                                                @NotNull PsiSymbolReferenceHints hints) {
    final LanguageReferenceProviders languageReferenceProviders = ReferenceProviders.getInstance().byLanguage(element.getLanguage());
    final List<PsiSymbolReferenceProvider> providers = languageReferenceProviders.getProviders(element);
    final List<PsiSymbolReference> result = new SmartList<>();
    for (PsiSymbolReferenceProvider provider : providers) {
      result.addAll(provider.getReferences(element, hints));
    }
    return result;
  }

  @NotNull
  private static List<PsiSymbolReference> applyHints(@NotNull List<PsiSymbolReference> references,
                                                     @NotNull PsiSymbolReferenceHints hints) {
    if (hints == EMPTY_HINTS) {
      return references;
    }
    List<PsiSymbolReference> result = references;
    Integer offsetInElement = hints.getOffsetInElement();
    if (offsetInElement != null) {
      result = ContainerUtil.filter(result, it -> ReferenceRange.containsOffsetInElement(it, offsetInElement));
    }
    // consider checking SymbolReference.resolvesTo(target) here if all needed
    return result;
  }
}
