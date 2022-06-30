// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@ApiStatus.Internal
public final class DisabledPluginsState implements PluginEnabler.Headless {

  public static final @NonNls String DISABLED_PLUGINS_FILENAME = "disabled_plugins.txt";

  private static final boolean IGNORE_DISABLED_PLUGINS = Boolean.getBoolean("idea.ignore.disabled.plugins");

  private static volatile @Nullable Set<PluginId> ourDisabledPlugins;
  private static final List<Runnable> ourDisabledPluginListeners = new CopyOnWriteArrayList<>();

  private static volatile boolean ourIgnoredDisabledPlugins = IGNORE_DISABLED_PLUGINS;

  DisabledPluginsState() {
  }

  public static void addDisablePluginListener(@NotNull Runnable listener) {
    ourDisabledPluginListeners.add(listener);
  }

  public static void removeDisablePluginListener(@NotNull Runnable listener) {
    ourDisabledPluginListeners.remove(listener);
  }

  @Override
  public boolean isIgnoredDisabledPlugins() {
    return ourIgnoredDisabledPlugins;
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  @Override
  public void setIgnoredDisabledPlugins(boolean ignoredDisabledPlugins) {
    ourIgnoredDisabledPlugins = ignoredDisabledPlugins;
  }

  public static @NotNull Set<PluginId> loadDisabledPlugins() {
    Set<PluginId> disabledPlugins = new LinkedHashSet<>();

    Path file = getDefaultFilePath();
    if (!Files.isRegularFile(file)) {
      return disabledPlugins;
    }

    ApplicationInfoEx applicationInfo = ApplicationInfoImpl.getShadowInstance();
    List<String> requiredPlugins = splitByComma(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY);

    boolean updateFile = false;
    try (BufferedReader reader = Files.newBufferedReader(file)) {
      String id;
      while ((id = reader.readLine()) != null) {
        id = id.trim();
        if (id.isEmpty()) {
          continue;
        }

        if (!requiredPlugins.contains(id) &&
            !applicationInfo.isEssentialPlugin(id)) {
          disabledPlugins.add(PluginId.getId(id));
        }
        else {
          updateFile = true;
        }
      }

      for (String suppressedId : splitByComma("idea.suppressed.plugins.id")) {
        PluginId suppressedPluginId = PluginId.getId(suppressedId);
        if (!applicationInfo.isEssentialPlugin(suppressedPluginId) &&
            disabledPlugins.add(suppressedPluginId)) {
          updateFile = true;
        }
      }
    }
    catch (IOException e) {
      getLogger().info("Unable to load disabled plugins list from " + file, e);
    }
    finally {
      if (updateFile) {
        trySaveDisabledPlugins(disabledPlugins, false);
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
    if (ourIgnoredDisabledPlugins) {
      return new HashSet<>();
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

  @Override
  public boolean isDisabled(@NotNull PluginId pluginId) {
    return getDisabledIds().contains(pluginId);
  }

  @Override
  public boolean enable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    return enableById(IdeaPluginDescriptorImplKt.toPluginSet(descriptors));
  }

  @Override
  public boolean disable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    return disableById(IdeaPluginDescriptorImplKt.toPluginSet(descriptors));
  }

  @Override
  public boolean enableById(@NotNull Set<PluginId> pluginIds) {
    return setEnabledState(pluginIds, true);
  }

  @Override
  public boolean disableById(@NotNull Set<PluginId> pluginIds) {
    return setEnabledState(pluginIds, false);
  }

  static boolean setEnabledState(@NotNull Set<PluginId> plugins,
                                 boolean enabled) {
    Set<PluginId> disabled = getDisabledIds();
    boolean changed = enabled ?
                      disabled.removeAll(plugins) :
                      disabled.addAll(plugins);

    getLogger().info(joinedPluginIds(plugins, enabled));
    return changed && saveDisabledPluginsAndInvalidate(disabled);
  }

  public static boolean saveDisabledPluginsAndInvalidate(@NotNull Set<PluginId> pluginIds) {
    return trySaveDisabledPlugins(pluginIds,
                                  true);
  }

  private static boolean trySaveDisabledPlugins(@NotNull Set<PluginId> pluginIds,
                                                boolean invalidate) {
    try {
      PluginManagerCore.writePluginIdsToFile(getDefaultFilePath(),
                                             pluginIds);
    }
    catch (IOException e) {
      getLogger().warn("Unable to save disabled plugins list", e);
      return false;
    }

    if (invalidate) {
      invalidate();
    }

    for (Runnable listener : ourDisabledPluginListeners) {
      listener.run();
    }

    return true;
  }

  @TestOnly
  public static void saveDisabledPluginsAndInvalidate(@NotNull Path configPath,
                                                      String... pluginIds) throws IOException {
    PluginManagerCore.writePluginIdsToFile(configPath.resolve(DISABLED_PLUGINS_FILENAME),
                                           Arrays.asList(pluginIds));
    invalidate();
  }

  private static @NotNull Path getDefaultFilePath() {
    return PathManager.getConfigDir()
      .resolve(DISABLED_PLUGINS_FILENAME);
  }


  private static @NotNull Logger getLogger() {
    // do not use class reference here
    return Logger.getInstance("#com.intellij.ide.plugins.DisabledPluginsState");
  }

  static void invalidate() {
    ourDisabledPlugins = null;
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
