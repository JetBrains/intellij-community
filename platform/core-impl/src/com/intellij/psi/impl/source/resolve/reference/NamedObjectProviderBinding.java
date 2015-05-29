/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author maxim
 */
public abstract class NamedObjectProviderBinding implements ProviderBinding {
  private final Map<String, List<ProviderInfo<ElementPattern>>> myNamesToProvidersMap = new THashMap<String, List<ProviderInfo<ElementPattern>>>(5);
  private final Map<String, List<ProviderInfo<ElementPattern>>> myNamesToProvidersMapInsensitive = new THashMap<String, List<ProviderInfo<ElementPattern>>>(5);

  public void registerProvider(@NonNls @NotNull String[] names,
                               @NotNull ElementPattern filter,
                               boolean caseSensitive,
                               @NotNull PsiReferenceProvider provider,
                               final double priority) {
    final Map<String, List<ProviderInfo<ElementPattern>>> map = caseSensitive ? myNamesToProvidersMap : myNamesToProvidersMapInsensitive;

    for (final String attributeName : names) {
      String key = caseSensitive ? attributeName : attributeName.toLowerCase();
      List<ProviderInfo<ElementPattern>> psiReferenceProviders = map.get(key);

      if (psiReferenceProviders == null) {
        map.put(key, psiReferenceProviders = new SmartList<ProviderInfo<ElementPattern>>());
      }

      psiReferenceProviders.add(new ProviderInfo<ElementPattern>(provider, filter, priority));
    }
  }

  @Override
  public void addAcceptableReferenceProviders(@NotNull PsiElement position,
                                              @NotNull List<ProviderInfo<ProcessingContext>> list,
                                              @NotNull PsiReferenceService.Hints hints) {
    String name = getName(position);
    if (name != null) {
      addMatchingProviders(position, myNamesToProvidersMap.get(name), list, hints);
      addMatchingProviders(position, myNamesToProvidersMapInsensitive.get(name.toLowerCase()), list, hints);
    }
  }

  @Override
  public void unregisterProvider(@NotNull final PsiReferenceProvider provider) {
    for (final List<ProviderInfo<ElementPattern>> list : myNamesToProvidersMap.values()) {
      for (final ProviderInfo<ElementPattern> trinity : new ArrayList<ProviderInfo<ElementPattern>>(list)) {
        if (trinity.provider.equals(provider)) {
          list.remove(trinity);
        }
      }
    }
    for (final List<ProviderInfo<ElementPattern>> list : myNamesToProvidersMapInsensitive.values()) {
      for (final ProviderInfo<ElementPattern> trinity : new ArrayList<ProviderInfo<ElementPattern>>(list)) {
        if (trinity.provider.equals(provider)) {
          list.remove(trinity);
        }
      }
    }
  }

  @Nullable
  protected abstract String getName(@NotNull PsiElement position);

  static void addMatchingProviders(@NotNull PsiElement position,
                                   @Nullable final List<ProviderInfo<ElementPattern>> providerList,
                                   @NotNull Collection<ProviderInfo<ProcessingContext>> output,
                                   @NotNull PsiReferenceService.Hints hints) {
    if (providerList == null) return;

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < providerList.size(); i++) {
      ProviderInfo<ElementPattern> info = providerList.get(i);
      if (hints != PsiReferenceService.Hints.NO_HINTS && !info.provider.acceptsHints(position, hints)) {
        continue;
      }

      final ProcessingContext context = new ProcessingContext();
      if (hints != PsiReferenceService.Hints.NO_HINTS) {
        context.put(PsiReferenceService.HINTS, hints);
      }
      boolean suitable = false;
      try {
        suitable = info.processingContext.accepts(position, context);
      }
      catch (IndexNotReadyException ignored) {
      }
      if (suitable) {
        output.add(new ProviderInfo<ProcessingContext>(info.provider, context, info.priority));
      }
    }
  }
}
