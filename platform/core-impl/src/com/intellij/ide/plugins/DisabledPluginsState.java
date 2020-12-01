// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.io.NioFiles;
import org.jetbrains.annotations.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DisabledPluginsState {
  public static final @NonNls String DISABLED_PLUGINS_FILENAME = "disabled_plugins.txt";

  private static volatile @Nullable Set<PluginId> ourDisabledPlugins;
  private static final List<Runnable> ourDisabledPluginListeners = new CopyOnWriteArrayList<>();
  private static volatile boolean ourIgnoreDisabledPlugins;

  @ApiStatus.Internal
  public static void addDisablePluginListener(@NotNull Runnable listener) {
    ourDisabledPluginListeners.add(listener);
  }

  @ApiStatus.Internal
  public static void removeDisablePluginListener(@NotNull Runnable listener) {
    ourDisabledPluginListeners.remove(listener);
  }

  // For use in headless environment only
  @ApiStatus.Internal
  public static void setIgnoreDisabledPlugins(boolean ignoreDisabledPlugins) {
    ourIgnoreDisabledPlugins = ignoreDisabledPlugins;
  }

  @ApiStatus.Internal
  public static @NotNull Set<PluginId> loadDisabledPlugins() {
    Set<PluginId> disabledPlugins = new LinkedHashSet<>();

    Path file = Paths.get(PathManager.getConfigPath(), DISABLED_PLUGINS_FILENAME);
    if (!Files.isRegularFile(file)) {
      return disabledPlugins;
    }

    ApplicationInfoEx applicationInfo = ApplicationInfoImpl.getShadowInstance();
    List<String> requiredPlugins = splitByComma(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY);

    boolean updateDisablePluginsList = false;
    try (BufferedReader reader = Files.newBufferedReader(file)) {
      String id;
      while ((id = reader.readLine()) != null) {
        id = id.trim();
        if (id.isEmpty()) {
          continue;
        }

        if (!requiredPlugins.contains(id) && !applicationInfo.isEssentialPlugin(id)) {
          addIdTo(id, disabledPlugins);
        }
        else {
          updateDisablePluginsList = true;
        }
      }

      for (String suppressedId : getNonEssentialSuppressedPlugins(applicationInfo)) {
        if (addIdTo(suppressedId, disabledPlugins)) {
          updateDisablePluginsList = true;
        }
      }
    }
    catch (IOException e) {
      getLogger().info("Unable to load disabled plugins list from " + file, e);
    }
    finally {
      if (updateDisablePluginsList) {
        trySaveDisabledPlugins(file, disabledPlugins, false);
      }
    }

    return disabledPlugins;
  }

  public static @NotNull Set<PluginId> disabledPlugins() {
    return Collections.unmodifiableSet(getDisabledIds());
  }

  private static @NotNull Set<PluginId> getDisabledIds() {
    Set<PluginId> result = ourDisabledPlugins;
    if (result != null) {
      return result;
    }

    // to preserve the order of additions and removals
    if (ourIgnoreDisabledPlugins || System.getProperty("idea.ignore.disabled.plugins") != null) {
      return Collections.emptySet();
    }

    //noinspection SynchronizeOnThis
    synchronized (DisabledPluginsState.class) {
      result = ourDisabledPlugins;
      if (result == null) {
        result = loadDisabledPlugins();
        ourDisabledPlugins = result;
      }
      return result;
    }
  }

  public static boolean isDisabled(@NotNull PluginId pluginId) {
    return getDisabledIds().contains(pluginId);
  }

  /**
   * @deprecated Bad API, sorry. Please use {@link PluginManagerCore#isDisabled(PluginId)} to check plugin's state,
   * {@link #enablePlugin(PluginId)}/{@link #disablePlugin(PluginId)} for state management,
   * {@link #disabledPlugins()} to get an unmodifiable collection of all disabled plugins (rarely needed).
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static @NotNull List<String> getDisabledPlugins() {
    Set<PluginId> list = getDisabledIds();
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

  public static boolean disablePlugin(@NotNull PluginId id) {
    Set<PluginId> disabledPlugins = getDisabledIds();
    return disabledPlugins.add(id) && trySaveDisabledPlugins(disabledPlugins);
  }

  public static boolean enablePlugin(@NotNull PluginId id) {
    Set<PluginId> disabledPlugins = getDisabledIds();
    return disabledPlugins.remove(id) && trySaveDisabledPlugins(disabledPlugins);
  }

  public static void enablePlugins(@NotNull Collection<? extends PluginDescriptor> plugins, boolean enabled) {
    Set<PluginId> pluginIds = new LinkedHashSet<>(plugins.size());
    for (PluginDescriptor descriptor : plugins) {
      PluginId value = descriptor.getPluginId();
      if (value != null) {
        pluginIds.add(value);
      }
    }
    enablePluginsById(pluginIds, enabled);
  }

  public static void enablePluginsById(@NotNull Collection<PluginId> plugins, boolean enabled) {
    Set<PluginId> disabled = getDisabledIds();
    boolean changed = enabled ?
                      disabled.removeAll(plugins) :
                      disabled.addAll(plugins);

    Collection<PluginId> filteredPlugins = new ArrayList<>(plugins.size());
    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = PluginManagerCore.buildPluginIdMap();
    for (PluginId pluginId : plugins) {
      IdeaPluginDescriptor descriptor = pluginIdMap.get(pluginId);
      if (descriptor != null) {
        descriptor.setEnabled(enabled);
        filteredPlugins.add(pluginId);
      }
    }

    getLogger().info(joinedPluginIds(filteredPlugins, enabled));

    if (!changed) {
      return;
    }

    trySaveDisabledPlugins(disabled);
  }

  public static boolean trySaveDisabledPlugins(@NotNull Collection<PluginId> pluginIds) {
    return trySaveDisabledPlugins(PathManager.getConfigDir().resolve(DISABLED_PLUGINS_FILENAME), pluginIds, true);
  }

  private static boolean trySaveDisabledPlugins(@NotNull Path file,
                                                @NotNull Collection<PluginId> pluginIds,
                                                boolean invalidate) {
    try {
      saveDisabledPlugins(file, pluginIds, invalidate);
      return true;
    }
    catch (IOException e) {
      getLogger().warn("Unable to save disabled plugins list", e);
      return false;
    }
  }

  @TestOnly
  public static void saveDisabledPlugins(@NotNull Path configDir, String... ids) throws IOException {
    List<PluginId> pluginIds = new ArrayList<>();
    for (String id : ids) {
      addIdTo(id, pluginIds);
    }
    saveDisabledPlugins(configDir.resolve(DISABLED_PLUGINS_FILENAME), pluginIds, true);
  }

  private static void saveDisabledPlugins(@NotNull Path file,
                                          @NotNull Collection<PluginId> pluginIds,
                                          boolean invalidate) throws IOException {
    savePluginsList(pluginIds, file);
    if (invalidate) {
      invalidate();
    }
    for (Runnable listener : ourDisabledPluginListeners) {
      listener.run();
    }
  }

  public static void savePluginsList(@NotNull Collection<PluginId> ids, @NotNull Path file) throws IOException {
    NioFiles.createDirectories(file.getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(file)) {
      PluginManagerCore.writePluginsList(ids, writer);
    }
  }

  private static @NotNull Logger getLogger() {
    // do not use class reference here
    //noinspection SSBasedInspection
    return Logger.getInstance("#com.intellij.ide.plugins.DisabledPluginsState");
  }

  static void invalidate() {
    ourDisabledPlugins = null;
  }

  private static boolean addIdTo(@NotNull String id, @NotNull Collection<PluginId> pluginIds) {
    return pluginIds.add(PluginId.getId(id));
  }

  private static @NotNull List<String> getNonEssentialSuppressedPlugins(@NotNull ApplicationInfoEx applicationInfo) {
    List<String> suppressedPlugins = splitByComma("idea.suppressed.plugins.id");
    if (suppressedPlugins.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> result = new ArrayList<>(suppressedPlugins.size());
    for (String suppressedPlugin : suppressedPlugins) {
      if (!applicationInfo.isEssentialPlugin(suppressedPlugin)) {
        result.add(suppressedPlugin);
      }
    }
    return result;
  }

  private static @NotNull List<String> splitByComma(@NotNull String key) {
    String[] strings = System.getProperty(key, "").split(",");
    return strings.length == 0 || strings.length == 1 && strings[0].isEmpty() ?
           Collections.emptyList() :
           Arrays.asList(strings);
  }

  private static @NotNull String joinedPluginIds(@NotNull Collection<PluginId> pluginIds, boolean enabled) {
    StringBuilder buffer = new StringBuilder("Plugins to ")
      .append(enabled ? "enable" : "disable")
      .append(": [");

    for (Iterator<PluginId> iterator = pluginIds.iterator(); iterator.hasNext(); ) {
      buffer.append(iterator.next().getIdString());
      if (iterator.hasNext()) {
        buffer.append(", ");
      }
    }

    return buffer.append(']').toString();
  }
}
