// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SharedProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author maxim
 */
@ApiStatus.Internal
public abstract class NamedObjectProviderBinding implements ProviderBinding {
  /**
   * arrays inside these maps must be copy-on-write to avoid data races, since they can be read concurrently,
   * via {@link #addAcceptableReferenceProviders}
   */
  private final Map<String, @NotNull ProviderInfo<ElementPattern<?>>[]> myNamesToProvidersMap = new ConcurrentHashMap<>(5);
  private final Map<String, @NotNull ProviderInfo<ElementPattern<?>>[]> myNamesToProvidersMapInsensitive = new ConcurrentHashMap<>(5);

  synchronized
  public void registerProvider(@NonNls String @NotNull [] names,
                               @NotNull ElementPattern<?> filter,
                               boolean caseSensitive,
                               @NotNull PsiReferenceProvider provider,
                               double priority) {
    Map<String, @NotNull ProviderInfo<ElementPattern<?>>[]> map = caseSensitive ? myNamesToProvidersMap : myNamesToProvidersMapInsensitive;

    for (String attributeName : names) {
      String key = caseSensitive ? attributeName : StringUtil.toLowerCase(attributeName);
      ProviderInfo<ElementPattern<?>>[] psiReferenceProviders = map.get(key);

      ProviderInfo<ElementPattern<?>> newInfo = new ProviderInfo<>(provider, filter, priority);
      ProviderInfo<ElementPattern<?>>[] newProviders = psiReferenceProviders == null ? new ProviderInfo[]{newInfo} : ArrayUtil.append(psiReferenceProviders, newInfo);

      map.put(key, newProviders);
    }
  }

  @Override
  public void addAcceptableReferenceProviders(@NotNull PsiElement position,
                                              @NotNull List<? super ProviderInfo<ProcessingContext>> list,
                                              @NotNull PsiReferenceService.Hints hints) {
    String name = getName(position);
    if (name != null) {
      addMatchingProviders(position, myNamesToProvidersMap.get(name), list, hints);
      addMatchingProviders(position, myNamesToProvidersMapInsensitive.get(StringUtil.toLowerCase(name)), list, hints);
    }
  }

  synchronized
  @Override
  public void unregisterProvider(@NotNull PsiReferenceProvider provider) {
    for (Map.Entry<String, @NotNull ProviderInfo<ElementPattern<?>>[]> entry : myNamesToProvidersMap.entrySet()) {
      entry.setValue((ProviderInfo<ElementPattern<?>>[])removeFromArray(provider, entry.getValue()));
    }
    for (Map.Entry<String, @NotNull ProviderInfo<ElementPattern<?>>[]> entry : myNamesToProvidersMapInsensitive.entrySet()) {
      entry.setValue((ProviderInfo<ElementPattern<?>>[])removeFromArray(provider, entry.getValue()));
    }
  }

  static @NotNull ProviderInfo<?> @NotNull [] removeFromArray(@NotNull PsiReferenceProvider provider, @NotNull ProviderInfo<?> @NotNull [] array) {
    int i = ContainerUtil.indexOf(array, trinity -> trinity.provider.equals(provider));
    if (i != -1) {
      return ArrayUtil.remove(array, i, ProviderInfo.ARRAY_FACTORY);
    }
    return array;
  }

  boolean isEmpty() {
    return myNamesToProvidersMap.isEmpty() && myNamesToProvidersMapInsensitive.isEmpty();
  }

  protected abstract @Nullable String getName(@NotNull PsiElement position);

  static void addMatchingProviders(@NotNull PsiElement position,
                                   @NotNull ProviderInfo<ElementPattern<?>> @Nullable [] providerList,
                                   @NotNull Collection<? super ProviderInfo<ProcessingContext>> output,
                                   @NotNull PsiReferenceService.Hints hints) {
    if (providerList == null) return;
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
