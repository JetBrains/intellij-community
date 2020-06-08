// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class DisabledPluginsState {
  public static final String DISABLED_PLUGINS_FILENAME = "disabled_plugins.txt";

  private static volatile Set<PluginId> ourDisabledPlugins;
  private static @Nullable Runnable disabledPluginListener;

  @ApiStatus.Internal
  public static void setDisabledPluginListener(@NotNull Runnable value) {
    disabledPluginListener = value;
  }

  // For use in headless environment only
  public static void dontLoadDisabledPlugins() {
    ourDisabledPlugins = Collections.emptySet();
  }

  @ApiStatus.Internal
  public static void loadDisabledPlugins(@NotNull String configPath, @NotNull Collection<PluginId> disabledPlugins) {
    Path file = Paths.get(configPath, DISABLED_PLUGINS_FILENAME);
    if (!Files.isRegularFile(file)) {
      return;
    }

    List<String> requiredPlugins = StringUtil.split(System.getProperty(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY, ""), ",");
    try {
      boolean updateDisablePluginsList = false;
      try (BufferedReader reader = Files.newBufferedReader(file)) {
        String id;
        while ((id = reader.readLine()) != null) {
          id = id.trim();
          if (!requiredPlugins.contains(id) && !ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(id)) {
            disabledPlugins.add(PluginId.getId(id));
          }
          else {
            updateDisablePluginsList = true;
          }
        }
      }
      finally {
        if (updateDisablePluginsList) {
          PluginManagerCore.savePluginsList(disabledPlugins, file, false);
          fireEditDisablePlugins();
        }
      }
    }
    catch (IOException e) {
      getLogger().info("Unable to load disabled plugins list from " + file, e);
    }
  }

  public static @NotNull Set<PluginId> disabledPlugins() {
    return Collections.unmodifiableSet(getDisabledIds());
  }

  static @NotNull Set<PluginId> getDisabledIds() {
    Set<PluginId> result = ourDisabledPlugins;
    if (result != null) {
      return result;
    }

    // to preserve the order of additions and removals
    if (System.getProperty("idea.ignore.disabled.plugins") != null) {
      return Collections.emptySet();
    }

    //noinspection SynchronizeOnThis
    synchronized (PluginManagerCore.class) {
      result = ourDisabledPlugins;
      if (result != null) {
        return result;
      }

      result = new LinkedHashSet<>();
      loadDisabledPlugins(PathManager.getConfigPath(), result);
      ourDisabledPlugins = result;
    }
    return result;
  }

  /**
   * @deprecated Bad API, sorry. Please use {@link #isDisabled(PluginId)} to check plugin's state,
   * {@link #enablePlugin(PluginId)}/{@link #disablePlugin(PluginId)} for state management,
   * {@link #disabledPlugins()} to get an unmodifiable collection of all disabled plugins (rarely needed).
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static @NotNull List<String> getDisabledPlugins() {
    Set<PluginId> list = getDisabledIds();
    return new AbstractList<String>() {
      //<editor-fold desc="Just a ist-like immutable wrapper over a set; move along.">
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

  public static boolean disablePlugin(@NotNull PluginId id) {
    Set<PluginId> disabledPlugins = getDisabledIds();
    return disabledPlugins.add(id) && trySaveDisabledPlugins(disabledPlugins);
  }

  public static boolean enablePlugin(@NotNull PluginId id) {
    Set<PluginId> disabledPlugins = getDisabledIds();
    return disabledPlugins.remove(id) && trySaveDisabledPlugins(disabledPlugins);
  }

  public static void enablePlugins(@NotNull Collection<? extends PluginDescriptor> plugins, boolean enabled) {
    enablePluginsById(ContainerUtil.map(plugins, (plugin) -> plugin.getPluginId()), enabled);
  }

  public static void enablePluginsById(@NotNull Collection<PluginId> plugins, boolean enabled) {
    Set<PluginId> disabled = getDisabledIds();
    int sizeBefore = disabled.size();
    for (PluginId plugin : plugins) {
      if (enabled) {
        disabled.remove(plugin);
      }
      else {
        disabled.add(plugin);
      }
      IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(plugin);
      if (pluginDescriptor != null) {
        pluginDescriptor.setEnabled(enabled);
      }
    }

    if (sizeBefore == disabled.size()) {
      // nothing changed
      return;
    }

    trySaveDisabledPlugins(disabled);
  }

  static boolean trySaveDisabledPlugins(@NotNull Collection<PluginId> disabledPlugins) {
    try {
      saveDisabledPlugins(disabledPlugins, false);
      return true;
    }
    catch (IOException e) {
      getLogger().warn("Unable to save disabled plugins list", e);
      return false;
    }
  }

  public static void saveDisabledPlugins(@NotNull Collection<PluginId> ids, boolean append) throws IOException {
    saveDisabledPlugins(PathManager.getConfigPath(), ids, append);
  }

  public static void saveDisabledPlugins(@NotNull String configPath, @NotNull Collection<PluginId> ids, boolean append) throws IOException {
    Path plugins = Paths.get(configPath, DISABLED_PLUGINS_FILENAME);
    PluginManagerCore.savePluginsList(ids, plugins, append);
    ourDisabledPlugins = null;
    fireEditDisablePlugins();
  }

  private static void fireEditDisablePlugins() {
    if (disabledPluginListener != null) {
      disabledPluginListener.run();
    }
  }

  public static @NotNull Logger getLogger() {
    // do not use class reference here
    //noinspection SSBasedInspection
    return Logger.getInstance("#com.intellij.ide.plugins.DisabledPluginsState");
  }

  static void invalidate() {
    ourDisabledPlugins = null;
  }
}
