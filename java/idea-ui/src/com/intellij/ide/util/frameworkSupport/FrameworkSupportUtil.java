
package com.intellij.ide.util.frameworkSupport;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.Pair;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class FrameworkSupportUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil");

  private FrameworkSupportUtil() {
  }

  public static List<FrameworkSupportProvider> getProviders(@NotNull ModuleType moduleType) {
    return getProviders(moduleType, null);
  }

  public static List<FrameworkSupportProvider> getProviders(@NotNull Module module) {
    return getProviders(module.getModuleType(), module);
  }

  private static List<FrameworkSupportProvider> getProviders(@NotNull ModuleType moduleType, @Nullable Module module) {
    FrameworkSupportProvider[] providers = Extensions.getExtensions(FrameworkSupportProvider.EXTENSION_POINT);
    ArrayList<FrameworkSupportProvider> result = new ArrayList<FrameworkSupportProvider>();
    for (FrameworkSupportProvider provider : providers) {
      if (provider.isEnabledForModuleType(moduleType) && (module == null || !provider.isSupportAlreadyAdded(module))) {
        result.add(provider);
      }
    }
    return result;
  }

  public static List<FrameworkSupportProvider> getProviders(@NotNull ModuleBuilder builder) {
    ArrayList<FrameworkSupportProvider> result = new ArrayList<FrameworkSupportProvider>();
    for (FrameworkSupportProvider provider : Extensions.getExtensions(FrameworkSupportProvider.EXTENSION_POINT)) {
      if (provider.isEnabledForModuleBuilder(builder)) {
        result.add(provider);
      }
    }
    return result;
  }

  public static boolean hasProviders(final Module module) {
    List<FrameworkSupportProvider> providers = getProviders(module);
    for (FrameworkSupportProvider provider : providers) {
      if (provider.getUnderlyingFrameworkId() == null) {
        return true;
      }
    }
    return false;
  }

  public static Comparator<FrameworkSupportProvider> getFrameworkSupportProvidersComparator(final List<FrameworkSupportProvider> providers) {
    DFSTBuilder<FrameworkSupportProvider>
      builder = new DFSTBuilder<FrameworkSupportProvider>(GraphGenerator.create(CachingSemiGraph.create(
      new ProvidersGraph(providers))));
    if (!builder.isAcyclic()) {
      Pair<FrameworkSupportProvider,FrameworkSupportProvider> pair = builder.getCircularDependency();
      LOG.error("Circular dependency between providers '" + pair.getFirst().getId() + "' and '" + pair.getSecond().getId() + "' was found.");
    }

    return builder.comparator();
  }

  @Nullable
  public static FrameworkSupportProvider findProvider(@NotNull String id, final List<FrameworkSupportProvider> providers) {
    for (FrameworkSupportProvider provider : providers) {
      if (id.equals(provider.getId())) {
        return provider;
      }
    }
    LOG.info("Cannot find framework support provider '" + id + "'");
    return null;
  }

  private static class ProvidersGraph implements GraphGenerator.SemiGraph<FrameworkSupportProvider> {
    private final List<FrameworkSupportProvider> myFrameworkSupportProviders;

    public ProvidersGraph(final List<FrameworkSupportProvider> frameworkSupportProviders) {
      myFrameworkSupportProviders = new ArrayList<FrameworkSupportProvider>(frameworkSupportProviders);
    }

    public Collection<FrameworkSupportProvider> getNodes() {
      return myFrameworkSupportProviders;
    }

    public Iterator<FrameworkSupportProvider> getIn(final FrameworkSupportProvider provider) {
      String[] ids = provider.getPrecedingFrameworkProviderIds();
      List<FrameworkSupportProvider> dependencies = new ArrayList<FrameworkSupportProvider>();
      String underlyingId = provider.getUnderlyingFrameworkId();
      if (underlyingId != null) {
        FrameworkSupportProvider underlyingProvider = findProvider(underlyingId, myFrameworkSupportProviders);
        if (underlyingProvider != null) {
          dependencies.add(underlyingProvider);
        }
      }
      for (String id : ids) {
        FrameworkSupportProvider dependency = findProvider(id, myFrameworkSupportProviders);
        if (dependency != null) {
          dependencies.add(dependency);
        }
      }
      return dependencies.iterator();
    }
  }
}
