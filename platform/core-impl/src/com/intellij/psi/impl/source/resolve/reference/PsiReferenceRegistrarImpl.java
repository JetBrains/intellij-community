// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.patterns.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Dmitry Avdeev
 */
public class PsiReferenceRegistrarImpl extends PsiReferenceRegistrar {
  private static final Logger LOG = Logger.getInstance(PsiReferenceRegistrarImpl.class);
  private final Map<Class<?>, SimpleProviderBinding> myBindingsMap = new THashMap<>();
  private final Map<Class<?>, NamedObjectProviderBinding> myNamedBindingsMap = new THashMap<>();
  private final ConcurrentMap<Class<?>, ProviderBinding[]> myBindingCache;
  private boolean myInitialized;

  PsiReferenceRegistrarImpl() {
    myBindingCache = ConcurrentFactoryMap.createMap(key-> {
      List<ProviderBinding> result = new SmartList<>();
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
      return result.toArray(new ProviderBinding[0]);
      }
    );
  }

  void markInitialized() {
    myInitialized = true;
  }

  @Override
  public <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern,
                                                               @NotNull PsiReferenceProvider provider,
                                                               double priority) {
    registerReferenceProvider(pattern, provider, priority, null);
  }

  public <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern,
                                                               @NotNull PsiReferenceProvider provider,
                                                               double priority,
                                                               @Nullable Disposable parentDisposable) {
    if (myInitialized && !ApplicationManager.getApplication().isUnitTestMode() && parentDisposable == null) {
      LOG.error("Reference provider registration is only allowed from PsiReferenceContributor");
    }

    Class<?> scope = pattern.getCondition().getInitialCondition().getAcceptedClass();
    final List<PatternCondition<? super T>> conditions = pattern.getCondition().getConditions();
    for (PatternCondition<? super T> _condition : conditions) {
      if (!(_condition instanceof PsiNamePatternCondition)) {
        continue;
      }
      PsiNamePatternCondition<?> nameCondition = (PsiNamePatternCondition<?>)_condition;
      List<PatternCondition<? super String>> conditions1 = nameCondition.getNamePattern().getCondition().getConditions();
      for (PatternCondition<? super String> condition1 : conditions1) {
        if (condition1 instanceof ValuePatternCondition) {
          final Collection<String> strings = ((ValuePatternCondition)condition1).getValues();
          registerNamedReferenceProvider(ArrayUtilRt.toStringArray(strings), nameCondition, scope, true, provider, priority, pattern, parentDisposable);
          return;
        }
        if (condition1 instanceof CaseInsensitiveValuePatternCondition) {
          final String[] strings = ((CaseInsensitiveValuePatternCondition)condition1).getValues();
          registerNamedReferenceProvider(strings, nameCondition, scope, false, provider, priority, pattern, parentDisposable);
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
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> unregisterReferenceProvider(scope, provider));
    }

    myBindingCache.clear();
  }

  public void unregisterReferenceProvider(@NotNull Class<?> scope, @NotNull PsiReferenceProvider provider) {
    final SimpleProviderBinding binding = myBindingsMap.get(scope);
    if (binding != null) {
      binding.unregisterProvider(provider);
      if (binding.isEmpty()) {
        myBindingsMap.remove(scope);
      }
    }
    myBindingCache.clear();
  }

  private void registerNamedReferenceProvider(String @NotNull [] names,
                                              final PsiNamePatternCondition<?> nameCondition,
                                              @NotNull Class<?> scopeClass,
                                              final boolean caseSensitive,
                                              @NotNull PsiReferenceProvider provider,
                                              final double priority,
                                              @NotNull ElementPattern<?> pattern,
                                              @Nullable Disposable parentDisposable) {
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
    if (parentDisposable != null) {
      NamedObjectProviderBinding finalProviderBinding = providerBinding;
      Disposer.register(parentDisposable, () -> {
        finalProviderBinding.unregisterProvider(provider);
        if (finalProviderBinding.isEmpty()) {
          myNamedBindingsMap.remove(scopeClass);
        }
      });
    }
  }

  @NotNull
  List<ProviderBinding.ProviderInfo<ProcessingContext>> getPairsByElement(@NotNull PsiElement element,
                                                                          @NotNull PsiReferenceService.Hints hints) {
    final ProviderBinding[] bindings = myBindingCache.get(element.getClass());
    if (bindings.length == 0) return Collections.emptyList();

    List<ProviderBinding.ProviderInfo<ProcessingContext>> ret = new SmartList<>();
    for (ProviderBinding binding : bindings) {
      binding.addAcceptableReferenceProviders(element, ret, hints);
    }
    return ret;
  }
}
