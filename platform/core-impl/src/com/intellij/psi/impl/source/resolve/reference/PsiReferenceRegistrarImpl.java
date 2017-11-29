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

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.patterns.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Dmitry Avdeev
 */
public class PsiReferenceRegistrarImpl extends PsiReferenceRegistrar {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl");
  private final Map<Class<?>, SimpleProviderBinding> myBindingsMap = ContainerUtil.newTroveMap();
  private final Map<Class<?>, NamedObjectProviderBinding> myNamedBindingsMap = ContainerUtil.newTroveMap();
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final ConcurrentMap<Class, ProviderBinding[]> myBindingCache;
  private boolean myInitialized;

  /**
   * @deprecated To be removed in 2018.2
   */
  @Deprecated
  @SuppressWarnings("unused")
  public PsiReferenceRegistrarImpl(final Language language) {
    this();
  }

  PsiReferenceRegistrarImpl() {
    myBindingCache = ConcurrentFactoryMap.createMap(key-> {
        List<ProviderBinding> result = ContainerUtil.newSmartList();
        for (Class<?> bindingClass : myBindingsMap.keySet()) {
          if (bindingClass.isAssignableFrom(key)) {
            result.add(myBindingsMap.get(bindingClass));
          }
        }
        for (Class<?> bindingClass : myNamedBindingsMap.keySet()) {
          if (bindingClass.isAssignableFrom(key)) {
            result.add(myNamedBindingsMap.get(bindingClass));
          }
        }
        //noinspection unchecked
        return result.toArray(new ProviderBinding[result.size()]);
      }
    );
  }

  public void markInitialized() {
    myInitialized = true;
  }

  @Override
  public <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern,
                                                               @NotNull PsiReferenceProvider provider,
                                                               double priority) {
    if (myInitialized && !ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error("Reference provider registration is only allowed from PsiReferenceContributor");
    }

    final Class scope = pattern.getCondition().getInitialCondition().getAcceptedClass();
    final List<PatternCondition<? super T>> conditions = pattern.getCondition().getConditions();
    for (PatternCondition<? super T> _condition : conditions) {
      if (!(_condition instanceof PsiNamePatternCondition)) {
        continue;
      }
      final PsiNamePatternCondition<?> nameCondition = (PsiNamePatternCondition)_condition;
      List<PatternCondition<? super String>> conditions1 = nameCondition.getNamePattern().getCondition().getConditions();
      for (PatternCondition<? super String> condition1 : conditions1) {
        if (condition1 instanceof ValuePatternCondition) {
          final Collection<String> strings = ((ValuePatternCondition)condition1).getValues();
          registerNamedReferenceProvider(ArrayUtil.toStringArray(strings), nameCondition, scope, true, provider, priority, pattern);
          return;
        }
        if (condition1 instanceof CaseInsensitiveValuePatternCondition) {
          final String[] strings = ((CaseInsensitiveValuePatternCondition)condition1).getValues();
          registerNamedReferenceProvider(strings, nameCondition, scope, false, provider, priority, pattern);
          return;
        }
      }
      break;
    }

    SimpleProviderBinding providerBinding = myBindingsMap.get(scope);
    if (providerBinding == null) {
      myBindingsMap.put(scope, providerBinding = new SimpleProviderBinding());
    }
    providerBinding.registerProvider(provider, pattern, priority);

    myBindingCache.clear();
  }

  public void unregisterReferenceProvider(@NotNull Class scope, @NotNull PsiReferenceProvider provider) {
    myBindingsMap.get(scope).unregisterProvider(provider);
  }


  private void registerNamedReferenceProvider(@NotNull String[] names,
                                              final PsiNamePatternCondition<?> nameCondition,
                                              @NotNull Class scopeClass,
                                              final boolean caseSensitive,
                                              @NotNull PsiReferenceProvider provider,
                                              final double priority,
                                              @NotNull ElementPattern pattern) {
    NamedObjectProviderBinding providerBinding = myNamedBindingsMap.get(scopeClass);

    if (providerBinding == null) {
      myNamedBindingsMap.put(scopeClass, providerBinding = new NamedObjectProviderBinding() {
        @Override
        protected String getName(@NotNull final PsiElement position) {
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
    registerReferenceProvider(PlatformPatterns.psiElement(scope), provider, DEFAULT_PRIORITY);
  }

  @NotNull
  List<ProviderBinding.ProviderInfo<ProcessingContext>> getPairsByElement(@NotNull PsiElement element,
                                                                                               @NotNull PsiReferenceService.Hints hints) {

    final ProviderBinding[] bindings = myBindingCache.get(element.getClass());
    if (bindings.length == 0) return Collections.emptyList();

    List<ProviderBinding.ProviderInfo<ProcessingContext>> ret = ContainerUtil.newSmartList();
    for (ProviderBinding binding : bindings) {
      binding.addAcceptableReferenceProviders(element, ret, hints);
    }
    return ret;
  }
}
