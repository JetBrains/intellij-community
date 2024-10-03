// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Dmitry Avdeev
 */
@ApiStatus.Internal
public class PsiReferenceRegistrarImpl extends PsiReferenceRegistrar {
  private static final Logger LOG = Logger.getInstance(PsiReferenceRegistrarImpl.class);
  private final Map<Class<?>, SimpleProviderBinding> myBindingsMap = new HashMap<>();
  private final Map<Class<?>, NamedObjectProviderBinding> myNamedBindingsMap = new HashMap<>();
  private final ConcurrentMap<Class<?>, ProviderBinding[]> myBindingCache;
  private boolean myInitialized;
  private final List<Disposable> myCleanupDisposables = new ArrayList<>();

  PsiReferenceRegistrarImpl() {
    myBindingCache = ConcurrentFactoryMap.createMap(key -> {
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

  void cleanup() {
    for (Disposable disposable : new ArrayList<>(myCleanupDisposables)) {
      Disposer.dispose(disposable);
    }
    myCleanupDisposables.clear();
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
    List<PatternCondition<? super T>> conditions = pattern.getCondition().getConditions();
    for (PatternCondition<? super T> _condition : conditions) {
      if (!(_condition instanceof PsiNamePatternCondition)) {
        continue;
      }
      PsiNamePatternCondition<?> nameCondition = (PsiNamePatternCondition<?>)_condition;
      List<PatternCondition<? super String>> conditions1 = nameCondition.getNamePattern().getCondition().getConditions();
      for (PatternCondition<? super String> condition1 : conditions1) {
        if (condition1 instanceof ValuePatternCondition) {
          Collection<String> strings = ((ValuePatternCondition)condition1).getValues();
          registerNamedReferenceProvider(ArrayUtilRt.toStringArray(strings), nameCondition, scope, true, provider, priority, pattern,
                                         parentDisposable);
          return;
        }
        if (condition1 instanceof CaseInsensitiveValuePatternCondition) {
          String[] strings = ((CaseInsensitiveValuePatternCondition)condition1).getValues();
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
      Disposable disposable = new Disposable() {
        @Override
        public void dispose() {
          PsiReferenceRegistrarImpl.this.unregisterReferenceProvider(scope, provider);
          myCleanupDisposables.remove(this);
        }

        @Override
        public String toString() {
          return "PsiReferenceRegistrarImpl cleanuper for " + provider +" ("+provider.getClass()+")";
        }
      };
      Disposer.register(parentDisposable, disposable);
      myCleanupDisposables.add(disposable);
    }

    clearBindingsCache();
  }

  void clearBindingsCache() {
    myBindingCache.clear();
  }

  public void unregisterReferenceProvider(@NotNull Class<?> scope, @NotNull PsiReferenceProvider provider) {
    SimpleProviderBinding binding = myBindingsMap.get(scope);
    if (binding != null) {
      binding.unregisterProvider(provider);
      if (binding.isEmpty()) {
        myBindingsMap.remove(scope);
      }
    }
    clearBindingsCache();
  }

  private void registerNamedReferenceProvider(String @NotNull [] names,
                                              PsiNamePatternCondition<?> nameCondition,
                                              @NotNull Class<?> scopeClass,
                                              boolean caseSensitive,
                                              @NotNull PsiReferenceProvider provider,
                                              double priority,
                                              @NotNull ElementPattern<?> pattern,
                                              @Nullable Disposable parentDisposable) {
    NamedObjectProviderBinding providerBinding = myNamedBindingsMap.get(scopeClass);

    if (providerBinding == null) {
      myNamedBindingsMap.put(scopeClass, providerBinding = new NamedObjectProviderBinding() {
        @Override
        protected String getName(@NotNull PsiElement position) {
          return nameCondition.getPropertyValue(position);
        }
      });
    }
    providerBinding.registerProvider(names, pattern, caseSensitive, provider, priority);
    if (parentDisposable != null) {
      NamedObjectProviderBinding finalProviderBinding = providerBinding;
      Disposable disposable = new Disposable() {
        @Override
        public void dispose() {
          finalProviderBinding.unregisterProvider(provider);
          if (finalProviderBinding.isEmpty()) {
            myNamedBindingsMap.remove(scopeClass);
          }
          myCleanupDisposables.remove(this);
        }
      };
      myCleanupDisposables.add(disposable);
      Disposer.register(parentDisposable, disposable);
    }
  }

  @ApiStatus.Internal
  @Unmodifiable
  @NotNull List<ProviderBinding.ProviderInfo<ProcessingContext>> getPairsByElement(@NotNull PsiElement element,
                                                                                   @NotNull PsiReferenceService.Hints hints) {
    ProviderBinding[] bindings = myBindingCache.get(element.getClass());
    if (bindings.length == 0) return Collections.emptyList();

    List<ProviderBinding.ProviderInfo<ProcessingContext>> ret = new SmartList<>();
    for (ProviderBinding binding : bindings) {
      binding.addAcceptableReferenceProviders(element, ret, hints);
    }
    return ret;
  }
  @ApiStatus.Internal
  @Unmodifiable
  public @NotNull List<PsiReferenceProvider> getPsiReferenceProvidersByElement(@NotNull PsiElement element,
                                                                               @NotNull PsiReferenceService.Hints hints) {
    return ContainerUtil.map(getPairsByElement(element, hints), info -> info.provider);
  }
}
