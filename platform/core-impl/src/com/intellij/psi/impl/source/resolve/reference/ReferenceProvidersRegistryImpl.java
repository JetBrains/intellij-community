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
import com.intellij.openapi.util.Comparing;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ReferenceProvidersRegistryImpl extends ReferenceProvidersRegistry {
  private static final LanguageExtension<PsiReferenceContributor> CONTRIBUTOR_EXTENSION = new LanguageExtension<PsiReferenceContributor>(PsiReferenceContributor.EP_NAME.getName());
  private static final LanguageExtension<PsiReferenceProviderBean> REFERENCE_PROVIDER_EXTENSION = new LanguageExtension<PsiReferenceProviderBean>(PsiReferenceProviderBean.EP_NAME.getName());

  private static final Comparator<ProviderBinding.ProviderInfo<ProcessingContext>> PRIORITY_COMPARATOR =
    new Comparator<ProviderBinding.ProviderInfo<ProcessingContext>>() {
      @Override
      public int compare(ProviderBinding.ProviderInfo<ProcessingContext> o1,
                         ProviderBinding.ProviderInfo<ProcessingContext> o2) {
        return Comparing.compare(o2.priority, o1.priority);
      }
    };

  private final Map<Language, PsiReferenceRegistrarImpl> myRegistrars = ContainerUtil.newConcurrentMap();

  @NotNull
  private static PsiReferenceRegistrarImpl createRegistrar(Language language) {
    PsiReferenceRegistrarImpl registrar = new PsiReferenceRegistrarImpl(language);
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
  protected PsiReference[] doGetReferencesFromProviders(@NotNull PsiElement context,
                                                        @NotNull PsiReferenceService.Hints hints) {
    List<ProviderBinding.ProviderInfo<ProcessingContext>> providers = getRegistrar(context.getLanguage()).getPairsByElement(context, hints);

    if (providers.isEmpty()) {
      return PsiReference.EMPTY_ARRAY;
    }

    if (providers.size() == 1) {
      return providers.get(0).provider.getReferencesByElement(context, providers.get(0).processingContext);
    }

    ContainerUtil.sort(providers, PRIORITY_COMPARATOR);

    List<PsiReference> result = new ArrayList<PsiReference>();
    final double maxPriority = providers.get(0).priority;
    next:
    for (ProviderBinding.ProviderInfo<ProcessingContext> trinity : providers) {
      final PsiReference[] refs;
      try {
        refs = trinity.provider.getReferencesByElement(context, trinity.processingContext);
      }
      catch(IndexNotReadyException ex) {
        continue;
      }
      if (trinity.priority != maxPriority) {
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
