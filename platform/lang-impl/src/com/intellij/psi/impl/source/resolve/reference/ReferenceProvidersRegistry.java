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

import com.intellij.codeInsight.completion.LegacyCompletionContributor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.Trinity;
import com.intellij.patterns.*;
import com.intellij.pom.references.PomReferenceProvider;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:13:45
 * To change this template use Options | File Templates.
 */
public class ReferenceProvidersRegistry extends PsiReferenceRegistrar {
  private static final PsiReferenceContributor[] ourExtensions = Extensions.getExtensions(PsiReferenceContributor.EP_NAME);
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

  private static final Comparator<Trinity<PsiReferenceProvider, ProcessingContext, Double>> PRIORITY_COMPARATOR =
    new Comparator<Trinity<PsiReferenceProvider, ProcessingContext, Double>>() {
      public int compare(final Trinity<PsiReferenceProvider, ProcessingContext, Double> o1,
                         final Trinity<PsiReferenceProvider, ProcessingContext, Double> o2) {
        return o2.getThird().compareTo(o1.getThird());
      }
    };
  private final Project myProject;

  private static final NotNullLazyKey<ReferenceProvidersRegistry, Project> INSTANCE_CACHE =
    ServiceManager.createLazyKey(ReferenceProvidersRegistry.class);

  public static ReferenceProvidersRegistry getInstance(Project project) {
    return INSTANCE_CACHE.getValue(project);
  }

  public ReferenceProvidersRegistry(Project project) {
    myProject = project;
    for (final PsiReferenceContributor contributor : ourExtensions) {
      contributor.registerReferenceProviders(this);
    }
  }

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
    myKnownSupers.clear();
    final Class scope = pattern.getCondition().getInitialCondition().getAcceptedClass();
    final PsiNamePatternCondition<?> nameCondition =
      ContainerUtil.findInstance(pattern.getCondition().getConditions(), PsiNamePatternCondition.class);
    if (nameCondition != null) {
      final ValuePatternCondition<String> valueCondition =
        ContainerUtil.findInstance(nameCondition.getNamePattern().getCondition().getConditions(), ValuePatternCondition.class);
      if (valueCondition != null) {
        final Collection<String> strings = valueCondition.getValues();
        registerNamedReferenceProvider(ArrayUtil.toStringArray(strings), new NamedObjectProviderBinding() {
          protected String getName(final PsiElement position) {
            return nameCondition.getPropertyValue(position);
          }
        }, scope, true, provider, priority, pattern);
        return;
      }

      final CaseInsensitiveValuePatternCondition ciCondition =
        ContainerUtil
          .findInstance(nameCondition.getNamePattern().getCondition().getConditions(), CaseInsensitiveValuePatternCondition.class);
      if (ciCondition != null) {
        registerNamedReferenceProvider(ciCondition.getValues(), new NamedObjectProviderBinding() {
          @Nullable
          protected String getName(final PsiElement position) {
            return nameCondition.getPropertyValue(position);
          }
        }, scope, false, provider, priority, pattern);
        return;
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

  public Project getProject() {
    return myProject;
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


  private void registerNamedReferenceProvider(final String[] names, final NamedObjectProviderBinding binding,
                                              final Class scopeClass,
                                              final boolean caseSensitive,
                                              final PsiReferenceProvider provider, final double priority, final ElementPattern pattern) {
    NamedObjectProviderBinding providerBinding = myNamedBindingsMap.get(scopeClass);

    if (providerBinding == null) {
      providerBinding = ConcurrencyUtil.cacheOrGet(myNamedBindingsMap, scopeClass, binding);
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
  public List<Trinity<PsiReferenceProvider, ProcessingContext, Double>> getPairsByElement(@NotNull PsiElement element,
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

  @Deprecated
  public static PsiReference[] getReferencesFromProviders(PsiElement context, @NotNull Class clazz) {
    return getReferencesFromProviders(context, PsiReferenceService.Hints.NO_HINTS);
  }

  public static PsiReference[] getReferencesFromProviders(PsiElement context, @NotNull PsiReferenceService.Hints hints) {
    ProgressManager.checkCanceled();
    assert context.isValid() : "Invalid context: " + context;

    final List<Trinity<PsiReferenceProvider, ProcessingContext, Double>> providers =
      getInstance(context.getProject()).getPairsByElement(context, hints);
    if (providers.isEmpty()) {
      return PsiReference.EMPTY_ARRAY;
    }

    if (providers.size() == 1) {
      final Trinity<PsiReferenceProvider, ProcessingContext, Double> firstProvider = providers.get(0);
      return firstProvider.getFirst().getReferencesByElement(context, firstProvider.getSecond());
    }

    ((SmartList<Trinity<PsiReferenceProvider, ProcessingContext, Double>>)providers).sort(PRIORITY_COMPARATOR);

    if (LegacyCompletionContributor.DEBUG) {
      System.out.println("ReferenceProvidersRegistry.getReferencesFromProviders");
      System.out.println("providers = " + providers);
    }

    List<PsiReference> result = new ArrayList<PsiReference>();
    final double maxPriority = providers.get(0).getThird();
    next:
    for (Trinity<PsiReferenceProvider, ProcessingContext, Double> trinity : providers) {
      final PsiReference[] refs = trinity.getFirst().getReferencesByElement(context, trinity.getSecond());
      if (trinity.getThird().doubleValue() != maxPriority) {
        for (PsiReference ref : refs) {
          for (PsiReference reference : result) {
            if (ref != null && ReferenceRange.containsRangeInElement(reference, ref.getRangeInElement())) {
              continue next;
            }
          }
        }
      }
      for (PsiReference ref : refs) {
        if (ref != null) {
          result.add(ref);
        }
      }
    }
    return result.isEmpty() ? PsiReference.EMPTY_ARRAY : ContainerUtil.toArray(result, new PsiReference[result.size()]);
  }
}
