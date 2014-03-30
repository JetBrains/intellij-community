/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import java.util.List;
import java.util.Map;

/**
 * @author maxim
 */
public abstract class NamedObjectProviderBinding<Provider> implements ProviderBinding<Provider> {
  private final Map<String, List<ProviderInfo<Provider, ElementPattern>>> myNamesToProvidersMap = new THashMap<String, List<ProviderInfo<Provider,ElementPattern>>>(5);
  private final Map<String, List<ProviderInfo<Provider, ElementPattern>>> myNamesToProvidersMapInsensitive = new THashMap<String, List<ProviderInfo<Provider, ElementPattern>>>(5);

  public void registerProvider(@NonNls @NotNull String[] names,
                               @NotNull ElementPattern filter,
                               boolean caseSensitive,
                               @NotNull Provider provider,
                               final double priority) {
    final Map<String, List<ProviderInfo<Provider, ElementPattern>>> map = caseSensitive ? myNamesToProvidersMap : myNamesToProvidersMapInsensitive;

    for (final String attributeName : names) {
      List<ProviderInfo<Provider, ElementPattern>> psiReferenceProviders = map.get(attributeName);

      if (psiReferenceProviders == null) {
        String key = caseSensitive ? attributeName : attributeName.toLowerCase();
        map.put(key, psiReferenceProviders = new SmartList<ProviderInfo<Provider, ElementPattern>>());
      }

      psiReferenceProviders.add(new ProviderInfo<Provider,ElementPattern>(provider, filter, priority));
    }
  }

  @Override
  public void addAcceptableReferenceProviders(@NotNull PsiElement position,
                                              @NotNull List<ProviderInfo<Provider, ProcessingContext>> list,
                                              @NotNull PsiReferenceService.Hints hints) {
    String name = getName(position);
    if (name != null) {
      addMatchingProviders(position, myNamesToProvidersMap.get(name), list, hints);
      addMatchingProviders(position, myNamesToProvidersMapInsensitive.get(name.toLowerCase()), list, hints);
    }
  }

  @Override
  public void unregisterProvider(@NotNull final Provider provider) {
    for (final List<ProviderInfo<Provider, ElementPattern>> list : myNamesToProvidersMap.values()) {
      for (final ProviderInfo<Provider, ElementPattern> trinity : new ArrayList<ProviderInfo<Provider,ElementPattern>>(list)) {
        if (trinity.provider.equals(provider)) {
          list.remove(trinity);
        }
      }
    }
    for (final List<ProviderInfo<Provider, ElementPattern>> list : myNamesToProvidersMapInsensitive.values()) {
      for (final ProviderInfo<Provider, ElementPattern> trinity : new ArrayList<ProviderInfo<Provider,ElementPattern>>(list)) {
        if (trinity.provider.equals(provider)) {
          list.remove(trinity);
        }
      }
    }
  }

  @Nullable
  protected abstract String getName(final PsiElement position);

  private void addMatchingProviders(final PsiElement position,
                                    @Nullable final List<ProviderInfo<Provider, ElementPattern>> providerList,
                                    @NotNull List<ProviderInfo<Provider, ProcessingContext>> ret,
                                    PsiReferenceService.Hints hints) {
    if (providerList == null) return;

    for(ProviderInfo<Provider, ElementPattern> trinity:providerList) {
      if (hints != PsiReferenceService.Hints.NO_HINTS && !((PsiReferenceProvider)trinity.provider).acceptsHints(position, hints)) {
        continue;
      }

      final ProcessingContext context = new ProcessingContext();
      if (hints != PsiReferenceService.Hints.NO_HINTS) {
        context.put(PsiReferenceService.HINTS, hints);
      }
      boolean suitable = false;
      try {
        suitable = trinity.processingContext.accepts(position, context);
      }
      catch (IndexNotReadyException ignored) {
      }
      if (suitable) {
        ret.add(new ProviderInfo<Provider,ProcessingContext>(trinity.provider, context, trinity.priority));
      }
    }
  }
}
