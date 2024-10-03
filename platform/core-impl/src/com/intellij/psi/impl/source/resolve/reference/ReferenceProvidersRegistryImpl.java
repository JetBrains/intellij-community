// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.doubles.Double2ObjectMap;
import it.unimi.dsi.fastutil.doubles.Double2ObjectMaps;
import it.unimi.dsi.fastutil.doubles.Double2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ReferenceProvidersRegistryImpl extends ReferenceProvidersRegistry {
  private static final LanguageExtension<PsiReferenceContributor> CONTRIBUTOR_EXTENSION =
    new LanguageExtension<>(PsiReferenceContributor.EP_NAME);
  private static final LanguageExtension<PsiReferenceProviderBean> REFERENCE_PROVIDER_EXTENSION =
    new LanguageExtension<>(PsiReferenceProviderBean.EP_NAME.getName());

  private final Map<Language, PsiReferenceRegistrarImpl> myRegistrars = new ConcurrentHashMap<>();

  public ReferenceProvidersRegistryImpl() {
    if (ApplicationManager.getApplication().getExtensionArea().hasExtensionPoint(PsiReferenceContributor.EP_NAME)) {
      PsiReferenceContributor.EP_NAME.addExtensionPointListener(new ExtensionPointListener<KeyedLazyInstance<PsiReferenceContributor>>() {
        @Override
        public void extensionAdded(@NotNull KeyedLazyInstance<PsiReferenceContributor> extension,
                                   @NotNull PluginDescriptor pluginDescriptor) {
          reset();
        }

        @Override
        public void extensionRemoved(@NotNull KeyedLazyInstance<PsiReferenceContributor> extension,
                                     @NotNull PluginDescriptor pluginDescriptor) {
          reset();
        }

        private void reset() {
          // it is much easier to just initialize everything next time from scratch than maintain incremental updates
          for (PsiReferenceRegistrarImpl registrar : myRegistrars.values()) {
            registrar.cleanup();
            registrar.clearBindingsCache();
          }
          myRegistrars.clear();
        }
      }, null);
    }
  }

  private static @NotNull PsiReferenceRegistrarImpl createRegistrar(@NotNull Language language) {
    PsiReferenceRegistrarImpl registrar = new PsiReferenceRegistrarImpl();
    for (PsiReferenceContributor contributor : CONTRIBUTOR_EXTENSION.allForLanguageOrAny(language)) {
      registerContributedReferenceProviders(registrar, contributor);
    }

    List<PsiReferenceProviderBean> referenceProviderBeans = REFERENCE_PROVIDER_EXTENSION.allForLanguageOrAny(language);
    for (PsiReferenceProviderBean providerBean : referenceProviderBeans) {
      ElementPattern<PsiElement> pattern = providerBean.createElementPattern();
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

  private static void registerContributedReferenceProviders(@NotNull PsiReferenceRegistrarImpl registrar,
                                                            @NotNull PsiReferenceContributor contributor) {
    contributor.registerReferenceProviders(registrar);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull PsiReferenceRegistrarImpl getRegistrar(@NotNull Language language) {
    return myRegistrars.computeIfAbsent(language, ReferenceProvidersRegistryImpl::createRegistrar);
  }

  @Override
  public void unloadProvidersFor(@NotNull Language language) {
    PsiReferenceRegistrarImpl psiReferenceRegistrar = myRegistrars.remove(language);
    if (psiReferenceRegistrar != null) {
      psiReferenceRegistrar.cleanup();
    }
    for (PsiReferenceRegistrarImpl registrar : myRegistrars.values()) {
      registrar.clearBindingsCache();
    }
  }

  @Override
  // 1. we create priorities map: "priority" ->  non-empty references from providers
  //    if provider returns EMPTY_ARRAY or array with "null" references then this provider isn't added in priorities map.
  // 2. references with the highest priority are added "as is"
  // 3. all other references are added only they could be correctly merged with any reference with higher priority (ReferenceRange.containsRangeInElement(higherPriorityRef, lowerPriorityRef)
  protected PsiReference @NotNull [] doGetReferencesFromProviders(@NotNull PsiElement context,
                                                                  @NotNull PsiReferenceService.Hints hints) {
    List<ProviderBinding.ProviderInfo<ProcessingContext>> providers = getRegistrar(context.getLanguage()).getPairsByElement(context, hints);

    Double2ObjectMap<List<PsiReference[]>> allReferencesMap = mapNotEmptyReferencesFromProviders(context, providers);
    if (allReferencesMap.isEmpty()) {
      return PsiReference.EMPTY_ARRAY;
    }

    List<PsiReference> result = new SmartList<>();
    double maxPriority = Math.max(PsiReferenceRegistrar.LOWER_PRIORITY, ArrayUtil.max(allReferencesMap.keySet().toDoubleArray()));
    List<PsiReference> maxPriorityRefs = collectReferences(allReferencesMap.get(maxPriority));

    ContainerUtil.addAllNotNull(result, maxPriorityRefs);
    ContainerUtil.addAllNotNull(result, getLowerPriorityReferences(allReferencesMap, maxPriority, maxPriorityRefs));

    return result.toArray(PsiReference.EMPTY_ARRAY);
  }

  //  we create priorities map: "priority" ->  non-empty references from providers
  //  if provider returns EMPTY_ARRAY or array with "null" references then this provider isn't added in priorities map.
  private static @NotNull Double2ObjectMap<List<PsiReference[]>> mapNotEmptyReferencesFromProviders(@NotNull PsiElement context,
                                                                                                    @NotNull List<? extends ProviderBinding.ProviderInfo<ProcessingContext>> providers) {
    Double2ObjectOpenHashMap<List<PsiReference[]>> map = new Double2ObjectOpenHashMap<>();
    for (ProviderBinding.ProviderInfo<ProcessingContext> info : providers) {
      PsiReference[] refs = getReferences(context, info);
      if (refs.length > 0) {
        List<PsiReference[]> list = map.get(info.priority);
        if (list == null) {
          list = new SmartList<>();
          map.put(info.priority, list);
        }
        list.add(refs);
        if (IdempotenceChecker.isLoggingEnabled()) {
          IdempotenceChecker.logTrace(info.provider + " returned " + Arrays.toString(refs));
        }
      }
    }
    return map;
  }

  private static PsiReference @NotNull [] getReferences(@NotNull PsiElement context,
                                                        @NotNull ProviderBinding.ProviderInfo<ProcessingContext> providerInfo) {
    try {
      return providerInfo.provider.getReferencesByElement(context, providerInfo.processingContext);
    }
    catch (IndexNotReadyException ignored) {
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static @NotNull List<PsiReference> getLowerPriorityReferences(@NotNull Double2ObjectMap<List<PsiReference[]>> allReferencesMap,
                                                                        double maxPriority,
                                                                        @NotNull List<? extends PsiReference> maxPriorityRefs) {
    List<PsiReference> result = new SmartList<>();
    for (Double2ObjectMap.Entry<List<PsiReference[]>> entry : Double2ObjectMaps.fastIterable(allReferencesMap)) {
      if (maxPriority != entry.getDoubleKey()) {
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
                                                      PsiReference @NotNull [] lowerPriorityRefs) {
    for (PsiReference ref : lowerPriorityRefs) {
      if (ref != null) {
        for (PsiReference reference : higherPriorityRefs) {
          if (reference instanceof PsiReferencesWrapper) continue;
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
