// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SharedProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author maxim
 */
public abstract class NamedObjectProviderBinding implements ProviderBinding {
  private final Map<String, List<ProviderInfo<ElementPattern<?>>>> myNamesToProvidersMap = new HashMap<>(5);
  private final Map<String, List<ProviderInfo<ElementPattern<?>>>> myNamesToProvidersMapInsensitive = new HashMap<>(5);

  public void registerProvider(@NonNls String @NotNull [] names,
                               @NotNull ElementPattern<?> filter,
                               boolean caseSensitive,
                               @NotNull PsiReferenceProvider provider,
                               double priority) {
    Map<String, List<ProviderInfo<ElementPattern<?>>>> map = caseSensitive ? myNamesToProvidersMap : myNamesToProvidersMapInsensitive;

    for (String attributeName : names) {
      String key = caseSensitive ? attributeName : StringUtil.toLowerCase(attributeName);
      List<ProviderInfo<ElementPattern<?>>> psiReferenceProviders = map.get(key);

      if (psiReferenceProviders == null) {
        map.put(key, psiReferenceProviders = new SmartList<>());
      }

      psiReferenceProviders.add(new ProviderInfo<>(provider, filter, priority));
    }
  }

  @Override
  public void addAcceptableReferenceProviders(@NotNull PsiElement position,
                                              @NotNull List<? super ProviderInfo<ProcessingContext>> list,
                                              @NotNull PsiReferenceService.Hints hints) {
    String name = getName(position);
    if (name != null) {
      addMatchingProviders(position, ContainerUtil.notNullize(myNamesToProvidersMap.get(name)), list, hints);
      addMatchingProviders(position, ContainerUtil.notNullize(myNamesToProvidersMapInsensitive.get(StringUtil.toLowerCase(name))), list, hints);
    }
  }

  @Override
  public void unregisterProvider(@NotNull PsiReferenceProvider provider) {
    for (List<ProviderInfo<ElementPattern<?>>> list : myNamesToProvidersMap.values()) {
      list.removeIf(trinity -> trinity.provider.equals(provider));
    }
    for (List<ProviderInfo<ElementPattern<?>>> list : myNamesToProvidersMapInsensitive.values()) {
      list.removeIf(trinity -> trinity.provider.equals(provider));
    }
  }

  boolean isEmpty() {
    return myNamesToProvidersMap.isEmpty() && myNamesToProvidersMapInsensitive.isEmpty();
  }

  @Nullable
  protected abstract String getName(@NotNull PsiElement position);

  static void addMatchingProviders(@NotNull PsiElement position,
                                   @NotNull List<? extends @NotNull ProviderInfo<ElementPattern<?>>> providerList,
                                   @NotNull Collection<? super ProviderInfo<ProcessingContext>> output,
                                   @NotNull PsiReferenceService.Hints hints) {
    SharedProcessingContext sharedProcessingContext = new SharedProcessingContext();

    for (ProviderInfo<ElementPattern<?>> info : providerList) {
      if (hints != PsiReferenceService.Hints.NO_HINTS && !info.provider.acceptsHints(position, hints)) {
        continue;
      }

      ProcessingContext context = new ProcessingContext(sharedProcessingContext);
      boolean suitable = false;
      try {
        suitable = info.processingContext.accepts(position, context);
      }
      catch (IndexNotReadyException ignored) {
      }
      if (suitable) {
        output.add(new ProviderInfo<>(info.provider, context, info.priority));
      }
    }
  }
}
