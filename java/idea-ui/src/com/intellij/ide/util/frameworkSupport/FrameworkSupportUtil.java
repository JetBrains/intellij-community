// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.frameworkSupport;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.newProjectWizard.OldFrameworkSupportProviderWrapper;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.InboundSemiGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class FrameworkSupportUtil {
  private static final Logger LOG = Logger.getInstance(FrameworkSupportUtil.class);

  private FrameworkSupportUtil() {
  }

  public static List<FrameworkSupportInModuleProvider> getProviders(@NotNull ModuleType moduleType, @NotNull FacetsProvider facetsProvider) {
    return getProviders(moduleType, null, facetsProvider);
  }

  public static List<FrameworkSupportInModuleProvider> getProviders(@NotNull Module module, final @NotNull FacetsProvider facetsProvider) {
    return getProviders(ModuleType.get(module), module, facetsProvider);
  }

  private static List<FrameworkSupportInModuleProvider> getProviders(@NotNull ModuleType moduleType,
                                                                     @Nullable Module module,
                                                                     @NotNull FacetsProvider facetsProvider) {
    List<FrameworkSupportInModuleProvider> allProviders = getAllProviders();
    List<FrameworkSupportInModuleProvider> result = new ArrayList<>();
    for (FrameworkSupportInModuleProvider provider : allProviders) {
      if (provider.isEnabledForModuleType(moduleType) && (module == null || provider.canAddSupport(module, facetsProvider))) {
        result.add(provider);
      }
    }
    return result;
  }

  public static List<FrameworkSupportInModuleProvider> getAllProviders() {
    List<FrameworkSupportInModuleProvider> allTypes = new ArrayList<>();
    for (FrameworkSupportProvider provider : FrameworkSupportProvider.EXTENSION_POINT.getExtensions()) {
      allTypes.add(new OldFrameworkSupportProviderWrapper(provider));
    }
    for (FrameworkTypeEx type : FrameworkTypeEx.EP_NAME.getExtensions()) {
      allTypes.add(type.createProvider());
    }
    return allTypes;
  }

  public static List<FrameworkSupportInModuleProvider> getProviders(@NotNull ModuleBuilder builder) {
    List<FrameworkSupportInModuleProvider> result = new ArrayList<>();
    for (FrameworkSupportInModuleProvider type : getAllProviders()) {
      if (type.isEnabledForModuleBuilder(builder)) {
        result.add(type);
      }
    }
    return result;
  }

  public static boolean hasProviders(final Module module, @NotNull FacetsProvider facetsProvider) {
    List<FrameworkSupportInModuleProvider> providers = getProviders(module, facetsProvider);
    for (FrameworkSupportInModuleProvider provider : providers) {
      if (provider.getFrameworkType().getUnderlyingFrameworkTypeId() == null) {
        return true;
      }
    }
    return false;
  }

  public static Comparator<FrameworkSupportInModuleProvider> getFrameworkSupportProvidersComparator(final List<? extends FrameworkSupportInModuleProvider> types) {
    DFSTBuilder<FrameworkSupportInModuleProvider>
      builder = new DFSTBuilder<>(GraphGenerator.generate(CachingSemiGraph.cache(new ProvidersGraph(types))));
    if (!builder.isAcyclic()) {
      Map.Entry<FrameworkSupportInModuleProvider, FrameworkSupportInModuleProvider> pair = builder.getCircularDependency();
      LOG.error("Circular dependency between types '" + pair.getKey().getFrameworkType().getId() + "' and '" + pair.getValue().getFrameworkType().getId() + "' was found.");
    }

    return builder.comparator();
  }

  public static FrameworkSupportInModuleProvider findProvider(@NotNull String id) {
    return findProvider(id, getAllProviders());
  }

  public static @Nullable FrameworkSupportInModuleProvider findProvider(@NotNull String id, final List<? extends FrameworkSupportInModuleProvider> providers) {
    for (FrameworkSupportInModuleProvider provider : providers) {
      String frameworkId = provider.getFrameworkType().getId();
      if (id.equals(frameworkId)
          || id.equals("facet:"+frameworkId)) {//we need this additional check for compatibility, e.g. id of web framework support provider was changed from 'facet:web' for 'web'
        return provider;
      }
    }
    LOG.info("Cannot find framework support provider '" + id + "'");
    return null;
  }

  private static class ProvidersGraph implements InboundSemiGraph<FrameworkSupportInModuleProvider> {
    private final List<FrameworkSupportInModuleProvider> myFrameworkSupportProviders;

    ProvidersGraph(final List<? extends FrameworkSupportInModuleProvider> frameworkSupportProviders) {
      myFrameworkSupportProviders = new ArrayList<>(frameworkSupportProviders);
    }

    @Override
    public @NotNull Collection<FrameworkSupportInModuleProvider> getNodes() {
      return myFrameworkSupportProviders;
    }

    @Override
    public @NotNull Iterator<FrameworkSupportInModuleProvider> getIn(final FrameworkSupportInModuleProvider provider) {
      List<FrameworkSupportInModuleProvider> dependencies = new ArrayList<>();
      String underlyingId = provider.getFrameworkType().getUnderlyingFrameworkTypeId();
      if (underlyingId != null) {
        FrameworkSupportInModuleProvider underlyingProvider = findProvider(underlyingId, myFrameworkSupportProviders);
        if (underlyingProvider != null) {
          dependencies.add(underlyingProvider);
        }
      }
      for (FrameworkSupportInModuleProvider.FrameworkDependency frameworkId : provider.getDependenciesFrameworkIds()) {
        FrameworkSupportInModuleProvider dep = findProvider(frameworkId.getFrameworkId(), myFrameworkSupportProviders);
        if (dep != null) {
          dependencies.add(dep);
        }
      }
      if (provider instanceof OldFrameworkSupportProviderWrapper) {
        String[] ids = ((OldFrameworkSupportProviderWrapper)provider).getProvider().getPrecedingFrameworkProviderIds();
        for (String id : ids) {
          FrameworkSupportInModuleProvider dependency = findProvider(id, myFrameworkSupportProviders);
          if (dependency != null) {
            dependencies.add(dependency);
          }
        }
      }
      return dependencies.iterator();
    }
  }
}