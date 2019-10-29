// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.util.IdempotenceChecker;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ReferenceProvidersRegistryImpl extends ReferenceProvidersRegistry {
  private static final LanguageExtension<PsiReferenceContributor> CONTRIBUTOR_EXTENSION =
    new LanguageExtension<>(PsiReferenceContributor.EP_NAME);
  private static final LanguageExtension<PsiReferenceProviderBean> REFERENCE_PROVIDER_EXTENSION =
    new LanguageExtension<>(PsiReferenceProviderBean.EP_NAME.getName());

  private final Map<Language, PsiReferenceRegistrarImpl> myRegistrars = ContainerUtil.newConcurrentMap();

  public ReferenceProvidersRegistryImpl() {
    if (Extensions.getRootArea().hasExtensionPoint(PsiReferenceContributor.EP_NAME)) {
      PsiReferenceContributor.EP_NAME.addExtensionPointListener(new ExtensionPointListener<KeyedLazyInstance<PsiReferenceContributor>>() {
        @Override
        public void extensionAdded(@NotNull KeyedLazyInstance<PsiReferenceContributor> extension,
                                   @NotNull PluginDescriptor pluginDescriptor) {
          Language language = Language.findLanguageByID(extension.getKey());
          if (language == Language.ANY) {
            for (PsiReferenceRegistrarImpl registrar : myRegistrars.values()) {
              registerContributedReferenceProviders(registrar, extension.getInstance());
            }
          }
          else if (language != null) {
            Set<Language> languageAndDialects = LanguageUtil.getAllDerivedLanguages(language);
            for (Language languageOrDialect : languageAndDialects) {
              final PsiReferenceRegistrarImpl registrar = myRegistrars.get(languageOrDialect);
              if (registrar != null) {
                registerContributedReferenceProviders(registrar, extension.getInstance());
              }
            }
          }
        }

        @Override
        public void extensionRemoved(@NotNull KeyedLazyInstance<PsiReferenceContributor> extension,
                                     @NotNull PluginDescriptor pluginDescriptor) {
          Disposer.dispose(extension.getInstance());
        }
      }, ApplicationManager.getApplication());
    }
  }

  @NotNull
  private static PsiReferenceRegistrarImpl createRegistrar(Language language) {
    PsiReferenceRegistrarImpl registrar = new PsiReferenceRegistrarImpl();
    for (PsiReferenceContributor contributor : CONTRIBUTOR_EXTENSION.allForLanguageOrAny(language)) {
      registerContributedReferenceProviders(registrar, contributor);
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

  private static void registerContributedReferenceProviders(PsiReferenceRegistrarImpl registrar, PsiReferenceContributor contributor) {
    contributor.registerReferenceProviders(new TrackingReferenceRegistrar(registrar, contributor));
    Disposer.register(ApplicationManager.getApplication(), contributor);
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

  @Override
  public void unloadProvidersFor(@NotNull Language language) {
    myRegistrars.remove(language);
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

    final List<PsiReference> result = new SmartList<>();
    final double maxPriority = getMaxPriority(allReferencesMap.keySet());
    final List<PsiReference> maxPriorityRefs = collectReferences(allReferencesMap.get(maxPriority));

    ContainerUtil.addAllNotNull(result, maxPriorityRefs);
    ContainerUtil.addAllNotNull(result, getLowerPriorityReferences(allReferencesMap, maxPriority, maxPriorityRefs));

    return result.toArray(PsiReference.EMPTY_ARRAY);
  }

  @NotNull
  //  we create priorities map: "priority" ->  non-empty references from providers
  //  if provider returns EMPTY_ARRAY or array with "null" references then this provider isn't added in priorities map.
  private static MultiMap<Double, PsiReference[]> mapNotEmptyReferencesFromProviders(@NotNull PsiElement context,
                                                                                     @NotNull List<? extends ProviderBinding.ProviderInfo<ProcessingContext>> providers) {
    MultiMap<Double, PsiReference[]> map = new MultiMap<>();
    for (ProviderBinding.ProviderInfo<ProcessingContext> trinity : providers) {
      final PsiReference[] refs = getReferences(context, trinity);
      if ((ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isInternal())
          && Registry.is("ide.check.reference.provider.underlying.element")) {
        assertReferenceUnderlyingElement(context, refs, trinity.provider);
      }
      if (refs.length > 0) {
        map.putValue(trinity.priority, refs);
        if (IdempotenceChecker.isLoggingEnabled()) {
          IdempotenceChecker.logTrace(trinity.provider + " returned " + Arrays.toString(refs));
        }
      }
    }
    return map;
  }

  private static void assertReferenceUnderlyingElement(@NotNull PsiElement context,
                                                       PsiReference[] refs, PsiReferenceProvider provider) {
    for (PsiReference reference : refs) {
      if (reference == null) continue;
      assert reference.getElement() == context : "reference " +
                                                 reference +
                                                 " was created for " +
                                                 context +
                                                 " but target " +
                                                 reference.getElement() +
                                                 ", provider " + provider;
    }
  }

  @NotNull
  private static PsiReference[] getReferences(@NotNull PsiElement context,
                                              @NotNull ProviderBinding.ProviderInfo<? extends ProcessingContext> providerInfo) {
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
                                                               @NotNull List<? extends PsiReference> maxPriorityRefs) {
    List<PsiReference> result = new SmartList<>();
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

  private static boolean haveNotIntersectedTextRanges(@NotNull List<? extends PsiReference> higherPriorityRefs,
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
    List<PsiReference> list = new SmartList<>();
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

  /**
   * @deprecated to attract attention and motivate to fix tests which fail these checks
   */
  @Deprecated
  public static void disableUnderlyingElementChecks(@NotNull Disposable parentDisposable) {
    Registry.get("ide.check.reference.provider.underlying.element").setValue(false, parentDisposable);
  }
}
