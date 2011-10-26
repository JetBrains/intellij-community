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

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Trinity;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ReferenceProvidersRegistryImpl extends ReferenceProvidersRegistry {
  private static final LanguageExtension<PsiReferenceContributor> CONTRIBUTOR_EXTENSION = new LanguageExtension<PsiReferenceContributor>(PsiReferenceContributor.EP_NAME.getName());
  private static final LanguageExtension<PsiReferenceProviderBean> REFERENCE_PROVIDER_EXTENSION = new LanguageExtension<PsiReferenceProviderBean>(PsiReferenceProviderBean.EP_NAME.getName());

  private static final Comparator<Trinity<PsiReferenceProvider, ProcessingContext, Double>> PRIORITY_COMPARATOR =
    new Comparator<Trinity<PsiReferenceProvider, ProcessingContext, Double>>() {
      @Override
      public int compare(final Trinity<PsiReferenceProvider, ProcessingContext, Double> o1,
                         final Trinity<PsiReferenceProvider, ProcessingContext, Double> o2) {
        return o2.getThird().compareTo(o1.getThird());
      }
    };

  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final Map<Language, PsiReferenceRegistrarImpl> myRegistrars = new FactoryMap<Language, PsiReferenceRegistrarImpl>() {
    @Override
    protected PsiReferenceRegistrarImpl create(Language language) {
      PsiReferenceRegistrarImpl registrar = new PsiReferenceRegistrarImpl();
      for (PsiReferenceContributor contributor : CONTRIBUTOR_EXTENSION.allForLanguage(language)) {
        contributor.registerReferenceProviders(registrar);
      }

      List<PsiReferenceProviderBean> referenceProviderBeans = REFERENCE_PROVIDER_EXTENSION.allForLanguage(language);
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
      return registrar;
    }
  };

  @Override
  public synchronized PsiReferenceRegistrarImpl getRegistrar(Language language) {
    return myRegistrars.get(language);
  }

  @Override
  protected PsiReference[] doGetReferencesFromProviders(PsiElement context,
                                                        PsiReferenceService.Hints hints) {
    List<Trinity<PsiReferenceProvider, ProcessingContext, Double>> providersForContextLanguage;
    providersForContextLanguage = getRegistrar(context.getLanguage()).getPairsByElement(context, hints);

    List<Trinity<PsiReferenceProvider, ProcessingContext, Double>> providersForAllLanguages;
    providersForAllLanguages = getRegistrar(Language.ANY).getPairsByElement(context, hints);

    int providersCount = providersForContextLanguage.size() + providersForAllLanguages.size();

    if (providersCount == 0) {
      return PsiReference.EMPTY_ARRAY;
    }

    if (providersCount == 1) {
      final Trinity<PsiReferenceProvider, ProcessingContext, Double> firstProvider =
        (providersForAllLanguages.isEmpty() ? providersForContextLanguage : providersForAllLanguages).get(0);
      return firstProvider.getFirst().getReferencesByElement(context, firstProvider.getSecond());
    }

    Trinity<PsiReferenceProvider, ProcessingContext, Double>[] providers = new Trinity[providersCount];

    int i = 0;
    for (Trinity<PsiReferenceProvider, ProcessingContext, Double> provider : providersForContextLanguage) {
      providers[i++] = provider;
    }
    for (Trinity<PsiReferenceProvider, ProcessingContext, Double> provider : providersForAllLanguages) {
      providers[i++] = provider;
    }

    Arrays.sort(providers, PRIORITY_COMPARATOR);

    List<PsiReference> result = new ArrayList<PsiReference>();
    final double maxPriority = providers[0].getThird();
    next:
    for (Trinity<PsiReferenceProvider, ProcessingContext, Double> trinity : providers) {
      final PsiReference[] refs;
      try {
        refs = trinity.getFirst().getReferencesByElement(context, trinity.getSecond());
      }
      catch(IndexNotReadyException ex) {
        continue;
      }
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
