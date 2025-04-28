// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.intellij.ide.plugins.PluginManagerCore.CORE_ID;

@Service
public final class PluginManager {
  public static final String INSTALLED_TXT = "installed.txt";
  public static final Pattern EXPLICIT_BIG_NUMBER_PATTERN = Pattern.compile("(.*)\\.(9{4,}+|10{4,}+)");

  public static @NotNull PluginManager getInstance() {
    return ApplicationManager.getApplication().getService(PluginManager.class);
  }

  private PluginManager() {}

  /**
   * @return file with a list of once installed plugins if it exists, null otherwise
   */
  public static @Nullable Path getOnceInstalledIfExists() {
    Path onceInstalledFile = PathManager.getConfigDir().resolve(INSTALLED_TXT);
    return Files.isRegularFile(onceInstalledFile) ? onceInstalledFile : null;
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

  /**
   * Tries to determine from which plugin does {@code aClass} come. Note that this method always returns {@code null} if IDE or tests are 
   * started from sources, because in that case the single classloader loads classes from all the plugins. So if you know ID of the plugin,
   * it's better to use {@link #findEnabledPlugin(PluginId)} instead.
   */
  public static @Nullable PluginDescriptor getPluginByClass(@NotNull Class<?> aClass) {
    ClassLoader loader = aClass.getClassLoader();
    return loader instanceof PluginAwareClassLoader ? ((PluginAwareClassLoader)loader).getPluginDescriptor() : null;
  }

  /**
   * @deprecated Use {@link #getPluginByClass}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @Nullable PluginId getPluginByClassName(@NotNull String className) {
    return getPluginByClassNameAsNoAccessToClass(className);
  }

  /**
   * Use only if {@link Class} is not available.
   */
  @ApiStatus.Internal
  public static @Nullable PluginId getPluginByClassNameAsNoAccessToClass(@NotNull String className) {
    PluginDescriptor result = PluginUtils.getPluginDescriptorOrPlatformByClassName(className);
    PluginId id = result == null ? null : result.getPluginId();
    return (id == null || CORE_ID.equals(id)) ? null : id;
  }

  public static @NotNull List<? extends IdeaPluginDescriptor> getLoadedPlugins() {
    return PluginManagerCore.getLoadedPlugins();
  }

  /**
   * @deprecated Bad API, sorry. Please use {@link PluginManagerCore#isDisabled(PluginId)} to check plugin's state,
   * {@link DisabledPluginsState#getDisabledIds()} to get an unmodifiable collection of all disabled plugins (rarely needed).
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @NotNull List<String> getDisabledPlugins() {
    Set<PluginId> list = DisabledPluginsState.Companion.getDisabledIds();
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
   * @deprecated Use {@link PluginManagerCore#enablePlugin(PluginId)}
   */
  @Deprecated
  public static boolean enablePlugin(@NotNull String id) {
    return PluginManagerCore.enablePlugin(PluginId.getId(id));
  }

  public boolean enablePlugin(@NotNull PluginId id) {
    return PluginManagerCore.enablePlugin(id);
  }

  /**
   * @deprecated Use own logger.
   */
  @Deprecated
  @ApiStatus.Internal
  public static @NotNull Logger getLogger() {
    return PluginManagerCore.getLogger();
  }

  public @Nullable IdeaPluginDescriptor findEnabledPlugin(@NotNull PluginId id) {
    return PluginManagerCore.INSTANCE.getPluginSet().findEnabledPlugin(id);
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

  @ApiStatus.Internal
  public static @NotNull Stream<IdeaPluginDescriptorImpl> getVisiblePlugins(boolean showImplementationDetails) {
    return filterVisiblePlugins(PluginManagerCore.INSTANCE.getPluginSet().allPlugins, showImplementationDetails);
  }

  @ApiStatus.Internal
  public static <T extends PluginDescriptor> @NotNull Stream<@NotNull T> filterVisiblePlugins(@NotNull Collection<@NotNull T> plugins,
                                                                                              boolean showImplementationDetails) {
    return plugins
      .stream()
      .filter(descriptor -> !descriptor.getPluginId().equals(CORE_ID))
      .filter(descriptor -> showImplementationDetails || !descriptor.isImplementationDetail());
  }
}
