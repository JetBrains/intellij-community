// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;

/**
 * @author yole
 */
public final class PluginEnabler {
  private static final Logger LOG = Logger.getInstance(PluginEnabler.class);

  public static boolean enablePlugins(Collection<IdeaPluginDescriptor> plugins, boolean enable) {
    return updatePluginEnabledState(enable ? plugins : Collections.emptyList(),
                                    enable ? Collections.emptyList() : plugins,
                                    null);
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  public static boolean updatePluginEnabledState(Collection<IdeaPluginDescriptor> pluginsToEnable,
                                                 Collection<IdeaPluginDescriptor> pluginsToDisable,
                                                 @Nullable JComponent parentComponent) {
    List<IdeaPluginDescriptorImpl> pluginDescriptorsToEnable = loadFullDescriptors(pluginsToEnable);
    List<IdeaPluginDescriptorImpl> pluginDescriptorsToDisable = loadFullDescriptors(pluginsToDisable);

    Set<PluginId> disabledIds = PluginManagerCore.getDisabledIds();
    for (PluginDescriptor descriptor : pluginsToEnable) {
      descriptor.setEnabled(true);
      disabledIds.remove(descriptor.getPluginId());
    }
    for (PluginDescriptor descriptor : pluginsToDisable) {
      descriptor.setEnabled(false);
      disabledIds.add(descriptor.getPluginId());
    }

    try {
      PluginManagerCore.saveDisabledPlugins(disabledIds, false);
    }
    catch (IOException e) {
      LOG.error(e);
    }

    if (ContainerUtil.all(pluginDescriptorsToDisable, DynamicPlugins::allowLoadUnloadWithoutRestart) &&
        ContainerUtil.all(pluginDescriptorsToEnable, DynamicPlugins::allowLoadUnloadWithoutRestart)) {
      boolean needRestart = false;
      for (IdeaPluginDescriptorImpl descriptor : pluginDescriptorsToDisable) {
        if (!DynamicPlugins.unloadPluginWithProgress(parentComponent, descriptor, true)) {
          needRestart = true;
          break;
        }
      }

      if (!needRestart) {
        for (IdeaPluginDescriptor descriptor : pluginDescriptorsToEnable) {
          DynamicPlugins.loadPlugin((IdeaPluginDescriptorImpl)descriptor);
        }
        return true;
      }
    }
    InstalledPluginsState.getInstance().setRestartRequired(true);
    return false;
  }

  private static List<IdeaPluginDescriptorImpl> loadFullDescriptors(Collection<IdeaPluginDescriptor> pluginsToEnable) {
    List<IdeaPluginDescriptorImpl> result = new ArrayList<>();
    for (IdeaPluginDescriptor descriptor : pluginsToEnable) {
      if (descriptor instanceof IdeaPluginDescriptorImpl) {
        result.add(loadFullDescriptor((IdeaPluginDescriptorImpl) descriptor));
      }
    }
    return result;
  }

  @Nullable
  public static IdeaPluginDescriptorImpl tryLoadFullDescriptor(@NotNull IdeaPluginDescriptorImpl descriptor) {
    PathBasedJdomXIncluder.PathResolver<?> resolver = createPathResolverForPlugin(descriptor, null);
    return PluginManager.loadDescriptor(descriptor.getPluginPath(), PluginManagerCore.PLUGIN_XML, Collections.emptySet(), descriptor.isBundled(), resolver);
  }

  @NotNull
  static PathBasedJdomXIncluder.PathResolver<?> createPathResolverForPlugin(@NotNull IdeaPluginDescriptorImpl descriptor,
                                                                            @Nullable DescriptorLoadingContext context) {
    if (PluginManagerCore.isRunningFromSources() &&
        descriptor.getPluginPath().getFileSystem().equals(FileSystems.getDefault()) &&
        descriptor.getPath().toString().contains("out/classes")) {
      return new ClassPathXmlPathResolver(descriptor.getPluginClassLoader());
    }

    if (context != null) {
      PathBasedJdomXIncluder.PathResolver<Path> resolver = PluginManagerCore.createPluginJarsPathResolver(descriptor.getPluginPath(), context);
      if (resolver != null) {
        return resolver;
      }
    }
    return PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER;
  }

  @NotNull
  public static IdeaPluginDescriptorImpl loadFullDescriptor(@NotNull IdeaPluginDescriptorImpl descriptor) {
    // PluginDescriptor fields are cleaned after the plugin is loaded, so we need to reload the descriptor to check if it's dynamic
    IdeaPluginDescriptorImpl fullDescriptor = tryLoadFullDescriptor(descriptor);
    if (fullDescriptor == null) {
      LOG.error("Could not load full descriptor for plugin " + descriptor.getPath());
      fullDescriptor = descriptor;
    }
    return fullDescriptor;
  }
}
