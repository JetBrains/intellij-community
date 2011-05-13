/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.Trinity;
import com.intellij.patterns.*;
import com.intellij.pom.references.PomReferenceProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Dmitry Avdeev
 */
public class PsiReferenceRegistrarImpl extends PsiReferenceRegistrar {

  private final ConcurrentMap<Class, SimpleProviderBinding> myBindingsMap = new ConcurrentHashMap<Class, SimpleProviderBinding>();
  private final ConcurrentMap<Class, NamedObjectProviderBinding> myNamedBindingsMap = new ConcurrentHashMap<Class, NamedObjectProviderBinding>();
  private final FactoryMap<Class, List<Class>> myKnownSupers = new ConcurrentFactoryMap<Class, List<Class>>() {
    @Override
    protected List<Class> create(Class key) {
      final Set<Class> result = new LinkedHashSet<Class>();
      for (Class candidate : myBindingsMap.keySet()) {
        if (candidate.isAssignableFrom(key)) {
          result.add(candidate);
        }
      }
      for (Class candidate : myNamedBindingsMap.keySet()) {
        if (candidate.isAssignableFrom(key)) {
          result.add(candidate);
        }
      }
      if (result.isEmpty()) {
        return Collections.emptyList();
      }
      return new ArrayList<Class>(result);
    }
  };

  /**
   * @deprecated {@see com.intellij.psi.PsiReferenceContributor
   */
  public void registerReferenceProvider(@Nullable ElementFilter elementFilter,
                                        @NotNull Class scope,
                                        @NotNull PsiReferenceProvider provider,
                                        double priority) {
    registerReferenceProvider(PlatformPatterns.psiElement(scope).and(new FilterPattern(elementFilter)), provider, priority);
  }

  public <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern,
                                                               @NotNull PomReferenceProvider<T> provider,
                                                               double priority) {
  }

  public <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern,
                                                               @NotNull PsiReferenceProvider provider,
                                                               double priority) {
    myKnownSupers.clear(); // we should clear the cache
    final Class scope = pattern.getCondition().getInitialCondition().getAcceptedClass();
    final List<PatternCondition<? super T>> conditions = pattern.getCondition().getConditions();
    for (int i = 0, conditionsSize = conditions.size(); i < conditionsSize; i++) {
      PatternCondition<? super T> _condition = conditions.get(i);
      if (_condition instanceof PsiNamePatternCondition) {
        final PsiNamePatternCondition<?> nameCondition = (PsiNamePatternCondition)_condition;
        List<PatternCondition<? super String>> conditions1 = nameCondition.getNamePattern().getCondition().getConditions();
        for (int i1 = 0, conditions1Size = conditions1.size(); i1 < conditions1Size; i1++) {
          PatternCondition<? super String> condition = conditions1.get(i1);
          if (condition instanceof ValuePatternCondition) {
            final Collection<String> strings = ((ValuePatternCondition)condition).getValues();
            registerNamedReferenceProvider(ArrayUtil.toStringArray(strings), nameCondition, scope, true, provider, priority, pattern);
            return;
          }
          if (condition instanceof CaseInsensitiveValuePatternCondition) {
            final String[] strings = ((CaseInsensitiveValuePatternCondition)condition).getValues();
            registerNamedReferenceProvider(strings, nameCondition, scope, false, provider, priority, pattern);
            return;
          }
        }
        break;
      }
    }

    while (true) {
      final SimpleProviderBinding providerBinding = myBindingsMap.get(scope);
      if (providerBinding != null) {
        providerBinding.registerProvider(provider, pattern, priority);
        return;
      }

      final SimpleProviderBinding binding = new SimpleProviderBinding();
      binding.registerProvider(provider, pattern, priority);
      if (myBindingsMap.putIfAbsent(scope, binding) == null) break;
    }
  }

  /**
   * @deprecated {@link com.intellij.psi.PsiReferenceContributor}
   */
  public void registerReferenceProvider(@Nullable ElementFilter elementFilter,
                                        @NotNull Class scope,
                                        @NotNull PsiReferenceProvider provider) {
    registerReferenceProvider(elementFilter, scope, provider, DEFAULT_PRIORITY);
  }

  public void unregisterReferenceProvider(@NotNull Class scope, @NotNull PsiReferenceProvider provider) {
    final ProviderBinding providerBinding = myBindingsMap.get(scope);
    providerBinding.unregisterProvider(provider);
  }


  private void registerNamedReferenceProvider(final String[] names, final PsiNamePatternCondition<?> nameCondition,
                                              final Class scopeClass,
                                              final boolean caseSensitive,
                                              final PsiReferenceProvider provider, final double priority, final ElementPattern pattern) {
    NamedObjectProviderBinding providerBinding = myNamedBindingsMap.get(scopeClass);

    if (providerBinding == null) {
      providerBinding = ConcurrencyUtil.cacheOrGet(myNamedBindingsMap, scopeClass, new NamedObjectProviderBinding() {
        protected String getName(final PsiElement position) {
          return nameCondition.getPropertyValue(position);
        }
      });
    }

    providerBinding.registerProvider(names, pattern, caseSensitive, provider, priority);
  }

  /**
   * @see com.intellij.psi.PsiReferenceContributor
   * @deprecated
   */
  public void registerReferenceProvider(@NotNull Class scope, @NotNull PsiReferenceProvider provider) {
    registerReferenceProvider(null, scope, provider);
  }

  @NotNull
  List<Trinity<PsiReferenceProvider, ProcessingContext, Double>> getPairsByElement(@NotNull PsiElement element,
                                                                                   @NotNull PsiReferenceService.Hints hints) {
    final Class<? extends PsiElement> clazz = element.getClass();
    List<Trinity<PsiReferenceProvider, ProcessingContext, Double>> ret = null;
    for (final Class aClass : myKnownSupers.get(clazz)) {
      final SimpleProviderBinding simpleBinding = myBindingsMap.get(aClass);
      final NamedObjectProviderBinding namedBinding = myNamedBindingsMap.get(aClass);
      if (simpleBinding == null && namedBinding == null) continue;

      if (ret == null) ret = new SmartList<Trinity<PsiReferenceProvider, ProcessingContext, Double>>();
      if (simpleBinding != null) {
        simpleBinding.addAcceptableReferenceProviders(element, ret, hints);
      }
      if (namedBinding != null) {
        namedBinding.addAcceptableReferenceProviders(element, ret, hints);
      }
    }
    return ret == null ? Collections.<Trinity<PsiReferenceProvider, ProcessingContext, Double>>emptyList() : ret;
  }
}
