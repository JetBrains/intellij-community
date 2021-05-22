// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SafeJdomFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphAlgorithms;
import com.intellij.util.graph.GraphGenerator;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

@Service
public final class PluginManager {
  public static final String INSTALLED_TXT = "installed.txt";

  public static @NotNull PluginManager getInstance() {
    return ApplicationManager.getApplication().getService(PluginManager.class);
  }

  private PluginManager() {}

  /**
   * @deprecated Use {@link DisabledPluginsState#addDisablePluginListener} directly
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public void addDisablePluginListener(@NotNull Runnable listener) {
    DisabledPluginsState.addDisablePluginListener(listener);
  }

  /**
   * @deprecated Use {@link DisabledPluginsState#removeDisablePluginListener} directly
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public void removeDisablePluginListener(@NotNull Runnable listener) {
    DisabledPluginsState.removeDisablePluginListener(listener);
  }

  /**
   * @return file with list of once installed plugins if it exists, null otherwise
   */
  public static @Nullable Path getOnceInstalledIfExists() {
    Path onceInstalledFile = PathManager.getConfigDir().resolve(INSTALLED_TXT);
    return Files.isRegularFile(onceInstalledFile) ? onceInstalledFile : null;
  }

  public static @Nullable IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path file,
                                                                  @NotNull Set<PluginId> disabledPlugins,
                                                                  boolean bundled,
                                                                  PathBasedJdomXIncluder.PathResolver<?> pathResolver) {
    DescriptorListLoadingContext parentContext = DescriptorListLoadingContext.createSingleDescriptorContext(disabledPlugins);
    try (DescriptorLoadingContext context = new DescriptorLoadingContext(parentContext, bundled, false, pathResolver)) {
      return PluginDescriptorLoader.loadDescriptorFromFileOrDir(file,
                                                                PluginManagerCore.PLUGIN_XML,
                                                                context,
                                                                Files.isDirectory(file));
    }
  }

  /**
   * @deprecated In a plugin code simply throw error or log using {@link Logger#error(Throwable)}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static void processException(@NotNull Throwable t) {
    try {
      Class<?> aClass = PluginManager.class.getClassLoader().loadClass("com.intellij.ide.plugins.StartupAbortedException");
      Method method = aClass.getMethod("processException", Throwable.class);
      method.setAccessible(true);
      method.invoke(null, t);
    }
    catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @deprecated Use {@link PluginManagerCore#getPlugin(PluginId)}
   */
  @Deprecated
  public static @Nullable IdeaPluginDescriptor getPlugin(@Nullable PluginId id) {
    return PluginManagerCore.getPlugin(id);
  }

  public static IdeaPluginDescriptor @NotNull [] getPlugins() {
    return PluginManagerCore.getPlugins();
  }

  public static boolean isPluginInstalled(PluginId id) {
    return PluginManagerCore.isPluginInstalled(id);
  }

  public static @Nullable PluginId getPluginByClassName(@NotNull String className) {
    return PluginManagerCore.getPluginByClassName(className);
  }

  public static @NotNull List<? extends IdeaPluginDescriptor> getLoadedPlugins() {
    return PluginManagerCore.getLoadedPlugins();
  }

  @SuppressWarnings("MethodMayBeStatic")
  public @Nullable PluginId getPluginOrPlatformByClassName(@NotNull String className) {
    return PluginManagerCore.getPluginOrPlatformByClassName(className);
  }

  /**
   * @deprecated Bad API, sorry. Please use {@link PluginManagerCore#isDisabled(PluginId)} to check plugin's state,
   * {@link DisabledPluginsState#disabledPlugins()} to get an unmodifiable collection of all disabled plugins (rarely needed).
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static @NotNull List<String> getDisabledPlugins() {
    return DisabledPluginsState.getDisabledPlugins();
  }

  public static boolean disablePlugin(@NotNull String id) {
    return PluginManagerCore.disablePlugin(PluginId.getId(id));
  }

  /**
   * @deprecated Use {@link DisabledPluginsState#enablePluginsById(Collection, boolean)}
   */
  @Deprecated
  public static boolean enablePlugin(@NotNull String id) {
    return PluginManagerCore.enablePlugin(PluginId.getId(id));
  }

  /**
   * Consider using {@link DisabledPluginsState#enablePluginsById(Collection, boolean)}.
   */
  @SuppressWarnings("MethodMayBeStatic")
  public boolean enablePlugin(@NotNull PluginId id) {
    return PluginManagerCore.enablePlugin(id);
  }

  @ApiStatus.Internal
  public static @NotNull Logger getLogger() {
    return PluginManagerCore.getLogger();
  }

  @ApiStatus.Internal
  public static void loadDescriptorFromFile(@NotNull IdeaPluginDescriptorImpl descriptor,
                                            @NotNull Path file,
                                            @Nullable SafeJdomFactory factory,
                                            @NotNull Set<PluginId> disabledPlugins) throws IOException, JDOMException {
    int flags = DescriptorListLoadingContext.IGNORE_MISSING_INCLUDE | DescriptorListLoadingContext.IGNORE_MISSING_SUB_DESCRIPTOR;
    DescriptorListLoadingContext parentContext = new DescriptorListLoadingContext(flags, disabledPlugins, PluginManagerCore.createLoadingResult(null));
    descriptor.readExternal(JDOMUtil.load(file, factory), PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER, parentContext, descriptor);
  }

  public boolean isDevelopedByJetBrains(@NotNull PluginDescriptor plugin) {
    return isDevelopedByJetBrains(plugin.getVendor());
  }

  @SuppressWarnings("MethodMayBeStatic")
  public boolean isDevelopedByJetBrains(@Nullable String vendorString) {
    if (vendorString == null) {
      return false;
    }

    if (vendorString.equals(PluginManagerCore.VENDOR_JETBRAINS) || vendorString.equals(PluginManagerCore.VENDOR_JETBRAINS_SRO)) {
      return true;
    }

    for (String vendor : StringUtil.split(vendorString, ",")) {
      String vendorItem = vendor.trim();
      if (PluginManagerCore.VENDOR_JETBRAINS.equals(vendorItem) || PluginManagerCore.VENDOR_JETBRAINS_SRO.equals(vendorItem)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("MethodMayBeStatic")
  public @Nullable IdeaPluginDescriptor findEnabledPlugin(@NotNull PluginId id) {
    List<IdeaPluginDescriptorImpl> result = PluginManagerCore.getLoadedPlugins(null);
    for (IdeaPluginDescriptor plugin : result) {
      if (id == plugin.getPluginId()) {
        return plugin;
      }
    }
    return null;
  }

  @SuppressWarnings("MethodMayBeStatic")
  public boolean hideImplementationDetails() {
    return !Registry.is("plugins.show.implementation.details");
  }

  @SuppressWarnings("MethodMayBeStatic")
  @ApiStatus.Internal
  public void setPlugins(@NotNull List<IdeaPluginDescriptor> descriptors) {
    @SuppressWarnings("SuspiciousToArrayCall")
    IdeaPluginDescriptorImpl[] list = descriptors.toArray(IdeaPluginDescriptorImpl.EMPTY_ARRAY);
    PluginManagerCore.doSetPlugins(list);
  }

  public boolean processAllBackwardDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                boolean withOptionalDeps,
                                                @NotNull Function<? super IdeaPluginDescriptor, FileVisitResult> consumer) {
    Map<PluginId, IdeaPluginDescriptorImpl> idMap = new HashMap<>();
    Collection<IdeaPluginDescriptorImpl> allPlugins = PluginManagerCore.getAllPlugins();
    for (IdeaPluginDescriptorImpl plugin : allPlugins) {
      idMap.put(plugin.getPluginId(), plugin);
    }

    CachingSemiGraph<IdeaPluginDescriptorImpl> semiGraph = PluginManagerCore
      .createPluginIdGraph(allPlugins,
                           idMap::get,
                           withOptionalDeps,
                           PluginManagerCore.findPluginByModuleDependency(PluginManagerCore.ALL_MODULES_MARKER) != null);
    Graph<IdeaPluginDescriptorImpl> graph = GraphGenerator.generate(semiGraph);
    Set<IdeaPluginDescriptorImpl> dependencies = new LinkedHashSet<>();
    GraphAlgorithms.getInstance().collectOutsRecursively(graph, rootDescriptor, dependencies);
    for (IdeaPluginDescriptorImpl dependency : dependencies) {
      if (dependency == rootDescriptor) {
        continue;
      }
      if (consumer.apply(dependency) == FileVisitResult.TERMINATE) {
        return false;
      }
    }
    return true;
  }

  public @NotNull Disposable createDisposable(@NotNull Class<?> requestor) {
    ClassLoader classLoader = requestor.getClassLoader();
    if (!(classLoader instanceof PluginAwareClassLoader)) {
      return Disposer.newDisposable();
    }

    int classLoaderId = ((PluginAwareClassLoader)classLoader).getInstanceId();
    // must not be lambda because we care about identity in ObjectTree.myObject2NodeMap
    return new PluginAwareDisposable() {
      @Override
      public int getClassLoaderId() {
        return classLoaderId;
      }

      @Override
      public void dispose() { }
    };
  }

  public @NotNull Disposable createDisposable(@NotNull Class<?> requestor, @NotNull ComponentManager parentDisposable) {
    Disposable disposable = createDisposable(requestor);
    Disposer.register(parentDisposable, disposable);
    return disposable;
  }

  interface PluginAwareDisposable extends Disposable {
    int getClassLoaderId();
  }
}
