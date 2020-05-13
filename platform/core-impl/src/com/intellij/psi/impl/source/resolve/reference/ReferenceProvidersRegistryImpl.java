// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.MetaLanguage;
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
import gnu.trove.TDoubleObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReferenceProvidersRegistryImpl extends ReferenceProvidersRegistry {
  private static final LanguageExtension<PsiReferenceContributor> CONTRIBUTOR_EXTENSION =
    new LanguageExtension<>(PsiReferenceContributor.EP_NAME);
  private static final LanguageExtension<PsiReferenceProviderBean> REFERENCE_PROVIDER_EXTENSION =
    new LanguageExtension<>(PsiReferenceProviderBean.EP_NAME.getName());

  private final Map<Language, PsiReferenceRegistrarImpl> myRegistrars = new ConcurrentHashMap<>();

  public ReferenceProvidersRegistryImpl() {
    if (Extensions.getRootArea().hasExtensionPoint(PsiReferenceContributor.EP_NAME)) {
      PsiReferenceContributor.EP_NAME.addExtensionPointListener(new ExtensionPointListener<KeyedLazyInstance<PsiReferenceContributor>>() {
        @Override
        public void extensionAdded(@NotNull KeyedLazyInstance<PsiReferenceContributor> extension,
                                   @NotNull PluginDescriptor pluginDescriptor) {
          Language language = Language.findLanguageByID(extension.getKey());
          PsiReferenceContributor instance = extension.getInstance();
          if (language == Language.ANY) {
            for (PsiReferenceRegistrarImpl registrar : myRegistrars.values()) {
              registerContributedReferenceProviders(registrar, instance);
            }
          }
          else if (language != null) {
            registerContributorForLanguageAndDialects(language, instance);
            if (language instanceof MetaLanguage) {
              Collection<Language> matchingLanguages = ((MetaLanguage)language).getMatchingLanguages();
              for (Language matchingLanguage : matchingLanguages) {
                registerContributorForLanguageAndDialects(matchingLanguage, instance);
              }
            }
          }
        }

        private void registerContributorForLanguageAndDialects(Language language, PsiReferenceContributor instance) {
          Set<Language> languageAndDialects = LanguageUtil.getAllDerivedLanguages(language);
          for (Language languageOrDialect : languageAndDialects) {
            final PsiReferenceRegistrarImpl registrar = myRegistrars.get(languageOrDialect);
            if (registrar != null) {
              registerContributedReferenceProviders(registrar, instance);
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

  private static @NotNull PsiReferenceRegistrarImpl createRegistrar(@NotNull Language language) {
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

          @Override
          public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
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

  private static void registerContributedReferenceProviders(@NotNull PsiReferenceRegistrarImpl registrar, @NotNull PsiReferenceContributor contributor) {
    contributor.registerReferenceProviders(new TrackingReferenceRegistrar(registrar, contributor));
    Disposer.register(ApplicationManager.getApplication(), contributor);
  }

  @Override
  public @NotNull PsiReferenceRegistrarImpl getRegistrar(@NotNull Language language) {
    return myRegistrars.computeIfAbsent(language, ReferenceProvidersRegistryImpl::createRegistrar);
  }

  @Override
  public void unloadProvidersFor(@NotNull Language language) {
    myRegistrars.remove(language);
  }

  @Override
  // 1. we create priorities map: "priority" ->  non-empty references from providers
  //    if provider returns EMPTY_ARRAY or array with "null" references then this provider isn't added in priorities map.
  // 2. references with the highest priority are added "as is"
  // 3. all other references are added only they could be correctly merged with any reference with higher priority (ReferenceRange.containsRangeInElement(higherPriorityRef, lowerPriorityRef)
  protected PsiReference @NotNull [] doGetReferencesFromProviders(@NotNull PsiElement context,
                                                                  @NotNull PsiReferenceService.Hints hints) {
    List<ProviderBinding.ProviderInfo<ProcessingContext>> providers = getRegistrar(context.getLanguage()).getPairsByElement(context, hints);

    TDoubleObjectHashMap<List<PsiReference[]>> allReferencesMap = mapNotEmptyReferencesFromProviders(context, providers);

    if (allReferencesMap.isEmpty()) return PsiReference.EMPTY_ARRAY;

    final List<PsiReference> result = new SmartList<>();
    double maxPriority = Math.max(PsiReferenceRegistrar.LOWER_PRIORITY, Arrays.stream(allReferencesMap.keys()).max().getAsDouble());
    List<PsiReference> maxPriorityRefs = collectReferences(allReferencesMap.get(maxPriority));

    ContainerUtil.addAllNotNull(result, maxPriorityRefs);
    ContainerUtil.addAllNotNull(result, getLowerPriorityReferences(allReferencesMap, maxPriority, maxPriorityRefs));

    return result.toArray(PsiReference.EMPTY_ARRAY);
  }

  //  we create priorities map: "priority" ->  non-empty references from providers
  //  if provider returns EMPTY_ARRAY or array with "null" references then this provider isn't added in priorities map.
  private static @NotNull TDoubleObjectHashMap<List<PsiReference[]>> mapNotEmptyReferencesFromProviders(@NotNull PsiElement context,
                                                                                     @NotNull List<? extends ProviderBinding.ProviderInfo<ProcessingContext>> providers) {
    TDoubleObjectHashMap<List<PsiReference[]>> map = new TDoubleObjectHashMap<>();
    for (ProviderBinding.ProviderInfo<ProcessingContext> trinity : providers) {
      final PsiReference[] refs = getReferences(context, trinity);
      if ((ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isInternal())
          && Registry.is("ide.check.reference.provider.underlying.element")) {
        assertReferenceUnderlyingElement(context, refs, trinity.provider);
      }
      if (refs.length > 0) {
        List<PsiReference[]> list = map.get(trinity.priority);
        if (list == null) {
          list = new SmartList<>();
          map.put(trinity.priority, list);
        }
        list.add(refs);
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

  private static PsiReference @NotNull [] getReferences(@NotNull PsiElement context,
                                                        @NotNull ProviderBinding.ProviderInfo<? extends ProcessingContext> providerInfo) {
    try {
      return providerInfo.provider.getReferencesByElement(context, providerInfo.processingContext);
    }
    catch (IndexNotReadyException ignored) {
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static @NotNull List<PsiReference> getLowerPriorityReferences(@NotNull TDoubleObjectHashMap<List<PsiReference[]>> allReferencesMap,
                                                                        double maxPriority,
                                                                        @NotNull List<? extends PsiReference> maxPriorityRefs) {
    List<PsiReference> result = new SmartList<>();
    allReferencesMap.forEachEntry((priority, referenceArrays) -> {
      if (maxPriority != priority) {
        for (PsiReference[] references : referenceArrays) {
          if (haveNotIntersectedTextRanges(maxPriorityRefs, references)) {
            ContainerUtil.addAllNotNull(result, references);
          }
        }
      }
      return true;
    });
    return result;
  }

  private static boolean haveNotIntersectedTextRanges(@NotNull List<? extends PsiReference> higherPriorityRefs,
                                                      PsiReference @NotNull [] lowerPriorityRefs) {
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

  private static @NotNull List<PsiReference> collectReferences(@Nullable Collection<PsiReference[]> references) {
    if (references == null) return Collections.emptyList();
    List<PsiReference> list = new SmartList<>();
    for (PsiReference[] reference : references) {
      ContainerUtil.addAllNotNull(list, reference);
    }

    return list;
  }

  /**
   * @deprecated to attract attention and motivate to fix tests which fail these checks
   */
  @Deprecated
  public static void disableUnderlyingElementChecks(@NotNull Disposable parentDisposable) {
    Registry.get("ide.check.reference.provider.underlying.element").setValue(false, parentDisposable);
  }
}
