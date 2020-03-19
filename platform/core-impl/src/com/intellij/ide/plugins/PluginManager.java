// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SafeJdomFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.intellij.ide.plugins.DescriptorListLoadingContext.IGNORE_MISSING_INCLUDE;

@Service
public final class PluginManager {
  public static final String INSTALLED_TXT = "installed.txt";

  private final List<Runnable> disabledPluginListeners = new CopyOnWriteArrayList<>();

  @ApiStatus.Internal
  public static final ConcurrentMap<PluginDescriptor, Disposable> pluginDisposables = ConcurrentFactoryMap.createWeakMap(
    plugin -> {
      Disposable pluginDisposable = Disposer.newDisposable("Plugin disposable [" + plugin.getName() + "]");
      Disposer.register(ApplicationManager.getApplication(), pluginDisposable);
      return pluginDisposable;
    }
  );

  @NotNull
  public static PluginManager getInstance() {
    return ApplicationManager.getApplication().getService(PluginManager.class);
  }

  private PluginManager() {
    PluginManagerCore.setDisabledPluginListener(() -> {
      for (Runnable listener : disabledPluginListeners) {
        listener.run();
      }
    });
  }

  public void addDisablePluginListener(@NotNull Runnable listener) {
    disabledPluginListeners.add(listener);
  }

  public void removeDisablePluginListener(@NotNull Runnable listener) {
    disabledPluginListeners.remove(listener);
  }

  /**
   * @return file with list of once installed plugins if it exists, null otherwise
   */
  @Nullable
  public static Path getOnceInstalledIfExists() {
    Path onceInstalledFile = PathManager.getConfigDir().resolve(INSTALLED_TXT);
    return Files.isRegularFile(onceInstalledFile) ? onceInstalledFile : null;
  }

  // not in PluginManagerCore because it is helper method
  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path file, @NotNull String fileName) {
    return loadDescriptor(file, fileName, PluginManagerCore.disabledPlugins(), false);
  }

  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path file,
                                                        @NotNull String fileName,
                                                        @Nullable Set<PluginId> disabledPlugins,
                                                        boolean bundled) {
    Set<PluginId> disabled = disabledPlugins == null ? Collections.emptySet() : disabledPlugins;
    DescriptorListLoadingContext parentContext = DescriptorListLoadingContext.createSingleDescriptorContext(disabled);
    try (DescriptorLoadingContext context = new DescriptorLoadingContext(parentContext, bundled, false, PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER)) {
      return PluginManagerCore.loadDescriptorFromFileOrDir(file, fileName, context, Files.isDirectory(file));
    }
  }

  /**
   * @deprecated In a plugin code simply throw error or log using {@link Logger#error(Throwable)}.
   */
  @Deprecated
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

  public static @NotNull IdeaPluginDescriptor[] getPlugins() {
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
   * {@link PluginManagerCore#disabledPlugins()} to get an unmodifiable collection of all disabled plugins (rarely needed).
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @NotNull
  public static List<String> getDisabledPlugins() {
    return PluginManagerCore.getDisabledPlugins();
  }

  /**
   * @deprecated Use {@link PluginManagerCore#saveDisabledPlugins(Collection, boolean)}
   */
  @Deprecated
  public static void saveDisabledPlugins(@NotNull Collection<String> ids, boolean append) throws IOException {
    PluginManagerCore.saveDisabledPlugins(ContainerUtil.map(ids, PluginId::getId), append);
  }

  public static boolean disablePlugin(@NotNull String id) {
    return PluginManagerCore.disablePlugin(PluginId.getId(id));
  }

  /**
   * @deprecated Use {@link #enablePlugins(Collection, boolean)}
   */
  @Deprecated
  public static boolean enablePlugin(@NotNull String id) {
    return PluginManagerCore.enablePlugin(PluginId.getId(id));
  }

  /**
   * Consider using {@link #enablePlugins(Collection, boolean)}.
   */
  public boolean enablePlugin(@NotNull PluginId id) {
    return PluginManagerCore.enablePlugin(id);
  }

  @NotNull
  public static Logger getLogger() {
    return PluginManagerCore.getLogger();
  }

  @ApiStatus.Internal
  public static void loadDescriptorFromFile(@NotNull IdeaPluginDescriptorImpl descriptor,
                                            @NotNull Path file,
                                            @Nullable SafeJdomFactory factory,
                                            boolean ignoreMissingInclude,
                                            @NotNull Set<PluginId> disabledPlugins) throws IOException, JDOMException {
    int flags = 0;
    if (ignoreMissingInclude) {
      flags |= IGNORE_MISSING_INCLUDE;
    }
    DescriptorListLoadingContext parentContext = new DescriptorListLoadingContext(flags, disabledPlugins, new PluginLoadingResult(Collections.emptyMap(), PluginManagerCore.getBuildNumber()));
    DescriptorLoadingContext context = new DescriptorLoadingContext(parentContext, descriptor.isBundled(), /* doesn't matter */ false,
                                                                    PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER);
    descriptor.readExternal(JDOMUtil.load(file, factory), file.getParent(), context.pathResolver, context, descriptor);
  }

  public boolean isDevelopedByJetBrains(@NotNull PluginDescriptor plugin) {
    return isDevelopedByJetBrains(plugin.getVendor());
  }

  public boolean isDevelopedByJetBrains(@Nullable String vendorString) {
    if (vendorString == null) {
      return false;
    }

    if (vendorString.equals(PluginManagerCore.VENDOR_JETBRAINS)) {
      return true;
    }

    for (String vendor : StringUtil.split(vendorString, ",")) {
      if (PluginManagerCore.VENDOR_JETBRAINS.equals(vendor.trim())) {
        return true;
      }
    }
    return false;
  }

  public void enablePlugins(@NotNull Collection<? extends PluginDescriptor> plugins, boolean enabled) {
    Set<PluginId> disabled = PluginManagerCore.getDisabledIds();
    int sizeBefore = disabled.size();
    for (PluginDescriptor plugin : plugins) {
      if (enabled) {
        disabled.remove(plugin.getPluginId());
      }
      else {
        disabled.add(plugin.getPluginId());
      }
      plugin.setEnabled(enabled);
    }

    if (sizeBefore == disabled.size()) {
      // nothing changed
      return;
    }

    PluginManagerCore.trySaveDisabledPlugins(disabled);
  }

  public @Nullable IdeaPluginDescriptor findEnabledPlugin(@NotNull PluginId id) {
    List<IdeaPluginDescriptorImpl> result = PluginManagerCore.ourLoadedPlugins;
    if (result == null) {
      return null;
    }

    for (IdeaPluginDescriptor plugin : result) {
      if (id == plugin.getPluginId()) {
        return plugin;
      }
    }
    return null;
  }

  public boolean hideImplementationDetails() {
    return !Registry.is("plugins.show.implementation.details");
  }

  @ApiStatus.Internal
  public void setPlugins(@NotNull List<IdeaPluginDescriptor> descriptors) {
    @SuppressWarnings("SuspiciousToArrayCall")
    IdeaPluginDescriptorImpl[] list = descriptors.toArray(IdeaPluginDescriptorImpl.EMPTY_ARRAY);
    PluginManagerCore.doSetPlugins(list);
  }
}