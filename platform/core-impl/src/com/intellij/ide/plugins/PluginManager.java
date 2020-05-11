// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SafeJdomFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public final class PluginManager {
  public static final String INSTALLED_TXT = "installed.txt";

  private final List<Runnable> disabledPluginListeners = new CopyOnWriteArrayList<>();

  public static @NotNull PluginManager getInstance() {
    return ApplicationManager.getApplication().getService(PluginManager.class);
  }

  private PluginManager() {
    DisabledPluginsState.setDisabledPluginListener(() -> {
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
  public static @Nullable Path getOnceInstalledIfExists() {
    Path onceInstalledFile = PathManager.getConfigDir().resolve(INSTALLED_TXT);
    return Files.isRegularFile(onceInstalledFile) ? onceInstalledFile : null;
  }

  // not in PluginManagerCore because it is helper method
  public static @Nullable IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path file, @NotNull String fileName) {
    return loadDescriptor(file, fileName, DisabledPluginsState.disabledPlugins(), false, PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER);
  }

  public static @Nullable IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path file,
                                                                  @NotNull String fileName,
                                                                  @NotNull Set<PluginId> disabledPlugins,
                                                                  boolean bundled,
                                                                  PathBasedJdomXIncluder.PathResolver<?> pathResolver) {
    DescriptorListLoadingContext parentContext = DescriptorListLoadingContext.createSingleDescriptorContext(disabledPlugins);
    try (DescriptorLoadingContext context = new DescriptorLoadingContext(parentContext, bundled, false, pathResolver)) {
      return PluginDescriptorLoader.loadDescriptorFromFileOrDir(file, fileName, context, Files.isDirectory(file));
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

  /**
   * @deprecated Use {@link DisabledPluginsState#saveDisabledPlugins(Collection, boolean)}
   */
  @Deprecated
  public static void saveDisabledPlugins(@NotNull Collection<String> ids, boolean append) throws IOException {
    DisabledPluginsState.saveDisabledPlugins(ContainerUtil.map(ids, PluginId::getId), append);
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

  @SuppressWarnings("MethodMayBeStatic")
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
}