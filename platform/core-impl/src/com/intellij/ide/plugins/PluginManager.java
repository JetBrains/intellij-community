// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SafeJdomFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.Decompressor;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
  public static @Nullable Path getOnceInstalledIfExists() {
    Path onceInstalledFile = PathManager.getConfigDir().resolve(INSTALLED_TXT);
    return Files.isRegularFile(onceInstalledFile) ? onceInstalledFile : null;
  }

  // not in PluginManagerCore because it is helper method
  public static @Nullable IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path file, @NotNull String fileName) {
    return loadDescriptor(file, fileName, PluginManagerCore.disabledPlugins(), false, PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER);
  }

  public static @Nullable IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path file,
                                                                  @NotNull String fileName,
                                                                  @NotNull Set<PluginId> disabledPlugins,
                                                                  boolean bundled,
                                                                  PathBasedJdomXIncluder.PathResolver<?> pathResolver) {
    DescriptorListLoadingContext parentContext = DescriptorListLoadingContext.createSingleDescriptorContext(disabledPlugins);
    try (DescriptorLoadingContext context = new DescriptorLoadingContext(parentContext, bundled, false, pathResolver)) {
      return PluginManagerCore.loadDescriptorFromFileOrDir(file, fileName, context, Files.isDirectory(file));
    }
  }

  public static @Nullable IdeaPluginDescriptorImpl loadDescriptorFromArtifact(@NotNull Path file, @Nullable BuildNumber buildNumber) throws IOException {
    DescriptorListLoadingContext parentContext = new DescriptorListLoadingContext(DescriptorListLoadingContext.IGNORE_MISSING_SUB_DESCRIPTOR, PluginManagerCore.disabledPlugins(),
                                                                                  PluginManagerCore.createLoadingResult(buildNumber));
    try (DescriptorLoadingContext context = new DescriptorLoadingContext(parentContext, false, false, PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER)) {
      IdeaPluginDescriptorImpl descriptor = PluginManagerCore.loadDescriptorFromFileOrDir(file, PluginManagerCore.PLUGIN_XML, context, false);
      if (descriptor == null && file.getFileName().toString().endsWith(".zip")) {
        File outputDir = FileUtil.createTempDirectory("plugin", "");
        try {
          new Decompressor.Zip(file.toFile()).extract(outputDir);
          File[] files = outputDir.listFiles();
          if (files != null && files.length == 1) {
            descriptor = PluginManagerCore.loadDescriptorFromFileOrDir(files[0].toPath(), PluginManagerCore.PLUGIN_XML, context, true);
          }
        }
        finally {
          FileUtil.delete(outputDir);
        }
      }
      return descriptor;
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
   * {@link PluginManagerCore#disabledPlugins()} to get an unmodifiable collection of all disabled plugins (rarely needed).
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static @NotNull List<String> getDisabledPlugins() {
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
    DescriptorLoadingContext context = new DescriptorLoadingContext(parentContext, descriptor.isBundled(), /* doesn't matter */ false,
                                                                    PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER);
    descriptor.readExternal(JDOMUtil.load(file, factory), context.pathResolver, context, descriptor);
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