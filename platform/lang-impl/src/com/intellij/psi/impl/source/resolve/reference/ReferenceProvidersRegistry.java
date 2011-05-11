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

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:13:45
 * To change this template use Options | File Templates.
 */
public class ReferenceProvidersRegistry {

  private static final LanguageExtension<PsiReferenceContributor> EXTENSION = new LanguageExtension<PsiReferenceContributor>(PsiReferenceContributor.EP_NAME.getName());

  private static final Comparator<Trinity<PsiReferenceProvider, ProcessingContext, Double>> PRIORITY_COMPARATOR =
    new Comparator<Trinity<PsiReferenceProvider, ProcessingContext, Double>>() {
      public int compare(final Trinity<PsiReferenceProvider, ProcessingContext, Double> o1,
                         final Trinity<PsiReferenceProvider, ProcessingContext, Double> o2) {
        return o2.getThird().compareTo(o1.getThird());
      }
    };

  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final static Map<Language, PsiReferenceRegistrarImpl> ourRegistrars = new FactoryMap<Language, PsiReferenceRegistrarImpl>() {
    @Override
    protected PsiReferenceRegistrarImpl create(Language language) {
      PsiReferenceRegistrarImpl registrar = new PsiReferenceRegistrarImpl();
      List<PsiReferenceContributor> contributors = EXTENSION.allForLanguage(language);
      for (PsiReferenceContributor contributor : contributors) {
        contributor.registerReferenceProviders(registrar);
      }
      return registrar;
    }
  };

  public static ReferenceProvidersRegistry getInstance() {
    return ServiceManager.getService(ReferenceProvidersRegistry.class);
  }

  public PsiReferenceRegistrar getRegistrar(Language language) {
    return ourRegistrars.get(language);
  }

  @Deprecated
  public static PsiReference[] getReferencesFromProviders(PsiElement context, @NotNull Class clazz) {
    return getReferencesFromProviders(context, PsiReferenceService.Hints.NO_HINTS);
  }

  public static PsiReference[] getReferencesFromProviders(PsiElement context, @NotNull PsiReferenceService.Hints hints) {
    ProgressManager.checkCanceled();
    assert context.isValid() : "Invalid context: " + context;

    PsiReferenceRegistrarImpl registrar = ourRegistrars.get(context.getLanguage());
    SmartList<Trinity<PsiReferenceProvider, ProcessingContext, Double>> providers = new SmartList<Trinity<PsiReferenceProvider, ProcessingContext, Double>>();
    providers.addAll(registrar.getPairsByElement(context, hints));
    providers.addAll(ourRegistrars.get(Language.ANY).getPairsByElement(context, hints));
    if (providers.isEmpty()) {
      return PsiReference.EMPTY_ARRAY;
    }

    if (providers.size() == 1) {
      final Trinity<PsiReferenceProvider, ProcessingContext, Double> firstProvider = providers.get(0);
      return firstProvider.getFirst().getReferencesByElement(context, firstProvider.getSecond());
    }

    providers.sort(PRIORITY_COMPARATOR);

    List<PsiReference> result = new ArrayList<PsiReference>();
    final double maxPriority = providers.get(0).getThird();
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
