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
import com.intellij.openapi.util.Trinity;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author maxim
 */
public abstract class NamedObjectProviderBinding<Provider> implements ProviderBinding<Provider> {
  private final ConcurrentMap<String, CopyOnWriteArrayList<Trinity<Provider, ElementPattern,Double>>> myNamesToProvidersMap = new ConcurrentHashMap<String, CopyOnWriteArrayList<Trinity<Provider, ElementPattern,Double>>>(5);
  private final ConcurrentMap<String, CopyOnWriteArrayList<Trinity<Provider, ElementPattern,Double>>> myNamesToProvidersMapInsensitive = new ConcurrentHashMap<String, CopyOnWriteArrayList<Trinity<Provider, ElementPattern,Double>>>(5);

  public void registerProvider(@NonNls String[] names, ElementPattern filter, boolean caseSensitive, Provider provider, final double priority) {
    final ConcurrentMap<String, CopyOnWriteArrayList<Trinity<Provider, ElementPattern,Double>>> map = caseSensitive ? myNamesToProvidersMap : myNamesToProvidersMapInsensitive;

    for (final String attributeName : names) {
      CopyOnWriteArrayList<Trinity<Provider, ElementPattern,Double>> psiReferenceProviders = map.get(attributeName);

      if (psiReferenceProviders == null) {
        psiReferenceProviders = ConcurrencyUtil.cacheOrGet(map, caseSensitive ? attributeName : attributeName.toLowerCase(), ContainerUtil.<Trinity<Provider, ElementPattern, Double>>createEmptyCOWList());
      }

      psiReferenceProviders.add(new Trinity<Provider, ElementPattern,Double>(provider, filter, priority));
    }
  }

  public void addAcceptableReferenceProviders(@NotNull PsiElement position, @NotNull List list,
                                              PsiReferenceService.Hints hints) {
    String name = getName(position);
    if (name != null) {
      addMatchingProviders(position, myNamesToProvidersMap.get(name), list, hints);
      addMatchingProviders(position, myNamesToProvidersMapInsensitive.get(name.toLowerCase()), list, hints);
    }
  }

  public void unregisterProvider(final Provider provider) {
    for (final CopyOnWriteArrayList<Trinity<Provider, ElementPattern, Double>> list : myNamesToProvidersMap.values()) {
      for (final Trinity<Provider, ElementPattern, Double> trinity : new ArrayList<Trinity<Provider, ElementPattern, Double>>(list)) {
        if (trinity.first.equals(provider)) {
          list.remove(trinity);
        }
      }
    }
    for (final CopyOnWriteArrayList<Trinity<Provider, ElementPattern, Double>> list : myNamesToProvidersMapInsensitive.values()) {
      for (final Trinity<Provider, ElementPattern, Double> trinity : new ArrayList<Trinity<Provider, ElementPattern, Double>>(list)) {
        if (trinity.first.equals(provider)) {
          list.remove(trinity);
        }
      }
    }
  }

  @Nullable
  abstract protected String getName(final PsiElement position);

  private static <T> void addMatchingProviders(final PsiElement position, @Nullable final List<Trinity<T, ElementPattern, Double>> providerList,
                                                                  final List<Trinity<T, ProcessingContext, Double>> ret,
                                                                  PsiReferenceService.Hints hints) {
    if (providerList == null) return;

    for(Trinity<T,ElementPattern,Double> trinity:providerList) {
      if (hints != PsiReferenceService.Hints.NO_HINTS && !((PsiReferenceProvider)trinity.first).acceptsHints(position, hints)) {
        continue;
      }

      final ProcessingContext context = new ProcessingContext();
      if (hints != PsiReferenceService.Hints.NO_HINTS) {
        context.put(PsiReferenceService.HINTS, hints);
      }
      boolean suitable = false;
      try {
        suitable = trinity.second.accepts(position, context);
      }
      catch (IndexNotReadyException ignored) {
      }
      if (suitable) {
        ret.add(Trinity.create(trinity.first, context, trinity.third));
      }
    }
  }
}
