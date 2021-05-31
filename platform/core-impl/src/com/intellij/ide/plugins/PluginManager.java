// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphAlgorithms;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.lang.Java11Shim;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public final class PluginManager {
  public static final String INSTALLED_TXT = "installed.txt";
  public static final Pattern EXPLICIT_BIG_NUMBER_PATTERN = Pattern.compile("(.*)\\.(9{4,}+|10{4,}+)");

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
    Set<PluginId> list = DisabledPluginsState.disabledPlugins();
    return new AbstractList<String>() {
      //<editor-fold desc="Just a list-like immutable wrapper over a set; move along.">
      @Override
      public boolean contains(Object o) {
        return list.contains(o);
      }

      @Override
      public int size() {
        return list.size();
      }

      @Override
      public String get(int index) {
        if (index < 0 || index >= list.size()) {
          throw new IndexOutOfBoundsException("index=" + index + " size=" + list.size());
        }
        Iterator<PluginId> iterator = list.iterator();
        for (int i = 0; i < index; i++) {
          iterator.next();
        }
        return iterator.next().getIdString();
      }
      //</editor-fold>
    };
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
  public boolean enablePlugin(@NotNull PluginId id) {
    return PluginManagerCore.enablePlugin(id);
  }

  @ApiStatus.Internal
  public static @NotNull Logger getLogger() {
    return PluginManagerCore.getLogger();
  }

  public @Nullable IdeaPluginDescriptor findEnabledPlugin(@NotNull PluginId id) {
    List<IdeaPluginDescriptorImpl> result = PluginManagerCore.getLoadedPlugins(null);
    for (IdeaPluginDescriptor plugin : result) {
      if (id.equals(plugin.getPluginId())) {
        return plugin;
      }
    }
    return null;
  }

  public boolean hideImplementationDetails() {
    return !Registry.is("plugins.show.implementation.details");
  }

  @ApiStatus.Internal
  public void setPlugins(@NotNull List<IdeaPluginDescriptorImpl> descriptors) {
    PluginManagerCore.doSetPlugins(Java11Shim.INSTANCE.copyOf(descriptors));
  }

  @ApiStatus.Internal
  public boolean processAllBackwardDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                boolean withOptionalDeps,
                                                @NotNull Function<IdeaPluginDescriptorImpl, FileVisitResult> consumer) {
    @NotNull PluginSet pluginSet = PluginManagerCore.getPluginSet();
    CachingSemiGraph<IdeaPluginDescriptorImpl> semiGraph = CachingSemiGraphKt.createPluginIdGraph(pluginSet.loadedPlugins, pluginSet,
                                                                                                  withOptionalDeps);
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

  /**
   * Convert build number like '146.9999' to '146.*' (like plugin repository does) to ensure that plugins which have such values in
   * 'until-build' attribute will be compatible with 146.SNAPSHOT build.
   */
  @ApiStatus.Internal
  public static @Nullable String convertExplicitBigNumberInUntilBuildToStar(@Nullable String build) {
    if (build == null) {
      return null;
    }

    Matcher matcher = EXPLICIT_BIG_NUMBER_PATTERN.matcher(build);
    return matcher.matches() ? (matcher.group(1) + ".*") : build;
  }
}
