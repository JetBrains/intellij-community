/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ReferenceProvidersRegistryImpl extends ReferenceProvidersRegistry {
  private static final LanguageExtension<PsiReferenceContributor> CONTRIBUTOR_EXTENSION =
    new LanguageExtension<>(PsiReferenceContributor.EP_NAME.getName());
  private static final LanguageExtension<PsiReferenceProviderBean> REFERENCE_PROVIDER_EXTENSION =
    new LanguageExtension<>(PsiReferenceProviderBean.EP_NAME.getName());

  private final Map<Language, PsiReferenceRegistrarImpl> myRegistrars = ContainerUtil.newConcurrentMap();

  @NotNull
  private static PsiReferenceRegistrarImpl createRegistrar(Language language) {
    PsiReferenceRegistrarImpl registrar = new PsiReferenceRegistrarImpl();
    for (PsiReferenceContributor contributor : CONTRIBUTOR_EXTENSION.allForLanguageOrAny(language)) {
      contributor.registerReferenceProviders(registrar);
    }

    List<PsiReferenceProviderBean> referenceProviderBeans = REFERENCE_PROVIDER_EXTENSION.allForLanguageOrAny(language);
    for (final PsiReferenceProviderBean providerBean : referenceProviderBeans) {
      final ElementPattern<PsiElement> pattern = providerBean.createElementPattern();
      if (pattern != null) {
        registrar.registerReferenceProvider(pattern, new PsiReferenceProvider() {

          PsiReferenceProvider myProvider;

          @NotNull
          @Override
          public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
            if (myProvider == null) {

              myProvider = providerBean.instantiate();
              if (myProvider == null) {
                myProvider = NULL_REFERENCE_PROVIDER;
              }
            }
            return myProvider.getReferencesByElement(element, context);
          }
        });
      }
    }

    registrar.markInitialized();

    return registrar;
  }


  @NotNull
  @Override
  public PsiReferenceRegistrarImpl getRegistrar(@NotNull Language language) {
    PsiReferenceRegistrarImpl registrar = myRegistrars.get(language);
    if (registrar == null) {
      //noinspection SynchronizeOnThis
      synchronized (this) {
        registrar = myRegistrars.get(language);
        if (registrar == null) {
          myRegistrars.put(language, registrar = createRegistrar(language));
        }
      }
    }
    return registrar;
  }

  @NotNull
  @Override
  // 1. we create priorities map: "priority" ->  non-empty references from providers
  //    if provider returns EMPTY_ARRAY or array with "null" references then this provider isn't added in priorities map.
  // 2. references with the highest priority are added "as is"
  // 3. all other references are added only they could be correctly merged with any reference with higher priority (ReferenceRange.containsRangeInElement(higherPriorityRef, lowerPriorityRef)
  protected PsiReference[] doGetReferencesFromProviders(@NotNull PsiElement context,
                                                        @NotNull PsiReferenceService.Hints hints) {
    List<ProviderBinding.ProviderInfo<ProcessingContext>> providers = getRegistrar(context.getLanguage()).getPairsByElement(context, hints);

    final MultiMap<Double, PsiReference[]> allReferencesMap = mapNotEmptyReferencesFromProviders(context, providers);

    if (allReferencesMap.isEmpty()) return PsiReference.EMPTY_ARRAY;

    final List<PsiReference> result = ContainerUtil.newSmartList();
    final double maxPriority = getMaxPriority(allReferencesMap.keySet());
    final List<PsiReference> maxPriorityRefs = collectReferences(allReferencesMap.get(maxPriority));

    ContainerUtil.addAllNotNull(result, maxPriorityRefs);
    ContainerUtil.addAllNotNull(result, getLowerPriorityReferences(allReferencesMap, maxPriority, maxPriorityRefs));

    return result.toArray(new PsiReference[result.size()]);
  }

  @NotNull
  //  we create priorities map: "priority" ->  non-empty references from providers
  //  if provider returns EMPTY_ARRAY or array with "null" references then this provider isn't added in priorities map.
  private static MultiMap<Double, PsiReference[]> mapNotEmptyReferencesFromProviders(@NotNull PsiElement context,
                                                                                     @NotNull List<ProviderBinding.ProviderInfo<ProcessingContext>> providers) {
    MultiMap<Double, PsiReference[]> map = new MultiMap<>();
    for (ProviderBinding.ProviderInfo<ProcessingContext> trinity : providers) {
      final PsiReference[] refs = getReferences(context, trinity);
      if (refs.length > 0) {
        map.putValue(trinity.priority, refs);
      }
    }
    return map;
  }

  @NotNull
  private static PsiReference[] getReferences(@NotNull PsiElement context,
                                              @NotNull ProviderBinding.ProviderInfo<ProcessingContext> providerInfo) {
    try {
      return providerInfo.provider.getReferencesByElement(context, providerInfo.processingContext);
    }
    catch (IndexNotReadyException ignored) {
    }
    return PsiReference.EMPTY_ARRAY;
  }

  @NotNull
  private static List<PsiReference> getLowerPriorityReferences(@NotNull MultiMap<Double, PsiReference[]> allReferencesMap,
                                                               double maxPriority,
                                                               @NotNull List<PsiReference> maxPriorityRefs) {
    List<PsiReference> result = ContainerUtil.newSmartList();
    for (Map.Entry<Double, Collection<PsiReference[]>> entry : allReferencesMap.entrySet()) {
      if (maxPriority != entry.getKey().doubleValue()) {
        for (PsiReference[] references : entry.getValue()) {
          if (haveNotIntersectedTextRanges(maxPriorityRefs, references)) {
            ContainerUtil.addAllNotNull(result, references);
          }
        }
      }
    }
    return result;
  }

  private static boolean haveNotIntersectedTextRanges(@NotNull List<PsiReference> higherPriorityRefs,
                                                      @NotNull  PsiReference[] lowerPriorityRefs) {
    for (PsiReference ref : lowerPriorityRefs) {
      if (ref != null) {
        for (PsiReference reference : higherPriorityRefs) {
          if (reference != null && ReferenceRange.containsRangeInElement(reference, ref.getRangeInElement())) {
            return false;
          }
        }
      }
    }
    return true;
  }

  @NotNull
  private static List<PsiReference> collectReferences(@Nullable Collection<PsiReference[]> references) {
    if (references == null) return Collections.emptyList();
    List<PsiReference> list = ContainerUtil.newSmartList();
    for (PsiReference[] reference : references) {
      ContainerUtil.addAllNotNull(list, reference);
    }

    return list;
  }

  private static double getMaxPriority(@NotNull Set<Double> doubles) {
    //return doubles.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
    double maxPriority = PsiReferenceRegistrar.LOWER_PRIORITY;
    for (Double aDouble : doubles) {
      if (aDouble.doubleValue() > maxPriority) maxPriority = aDouble.doubleValue();
    }
    return maxPriority;
  }
}
