// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.intellij.ide.plugins.PluginManagerCore.CORE_ID;

/**
 * @see PluginDetailsService for information about plugins for applied functionality
 */
@Service
public final class PluginManager {
  @ApiStatus.Internal
  public static final String INSTALLED_TXT = "installed.txt";

  @ApiStatus.Internal
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
   * Internal. Use {@link PluginDetailsService} instead in plugins.
   *
   * @deprecated Use {@link PluginManagerCore#getPlugin(PluginId)}
   */
  @Deprecated
  @ApiStatus.Internal
  public static @Nullable IdeaPluginDescriptor getPlugin(@Nullable PluginId id) {
    return PluginManagerCore.getPlugin(id);
  }

  /**
   * Internal. Use {@link PluginDetailsService} instead in plugins.
   */
  @ApiStatus.Internal
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
  @ApiStatus.Internal
  public static @Nullable PluginDescriptor getPluginByClass(@NotNull Class<?> aClass) {
    ClassLoader loader = aClass.getClassLoader();
    return loader instanceof PluginAwareClassLoader ? ((PluginAwareClassLoader)loader).getPluginDescriptor() : null;
  }

  /** @deprecated Use {@link #getPluginByClass} */
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

  /**
   * Internal. Use {@link PluginDetailsService} instead in plugins.
   */
  @ApiStatus.Internal
  public static @NotNull List<? extends IdeaPluginDescriptor> getLoadedPlugins() {
    return PluginManagerCore.getLoadedPlugins();
  }

  @ApiStatus.Internal
  public static boolean disablePlugin(@NotNull String id) {
    return PluginManagerCore.disablePlugin(PluginId.getId(id));
  }

  /** @deprecated Use {@link PluginManagerCore#enablePlugin(PluginId)} */
  @Deprecated
  @ApiStatus.Internal
  public static boolean enablePlugin(@NotNull String id) {
    return PluginManagerCore.enablePlugin(PluginId.getId(id));
  }

  @ApiStatus.Internal
  public boolean enablePlugin(@NotNull PluginId id) {
    return PluginManagerCore.enablePlugin(id);
  }

  /**
   * Internal. Use {@link PluginDetailsService} instead in plugins.
   */
  @ApiStatus.Internal
  public @Nullable IdeaPluginDescriptor findEnabledPlugin(@NotNull PluginId id) {
    return PluginManagerCore.getPluginSet().findEnabledPlugin(id);
  }

  /**
   * Convert build number like '146.9999' to '146.*' (like plugin repository does)
   * to ensure that plugins which have such values in the 'until-build' attribute will be compatible with 146.SNAPSHOT build.
   */
  @ApiStatus.Internal
  public static @Nullable String convertExplicitBigNumberInUntilBuildToStar(@Nullable String build) {
    if (build == null) return null;
    Matcher matcher = EXPLICIT_BIG_NUMBER_PATTERN.matcher(build);
    return matcher.matches() ? (matcher.group(1) + ".*") : build;
  }

  @ApiStatus.Internal
  public static @NotNull Stream<PluginMainDescriptor> getVisiblePlugins(boolean showImplementationDetails) {
    return filterVisiblePlugins(PluginManagerCore.getPluginSet().allPlugins, showImplementationDetails);
  }

  @ApiStatus.Internal
  public static <T extends PluginDescriptor> @NotNull Stream<@NotNull T> filterVisiblePlugins(
    @NotNull Collection<@NotNull T> plugins,
    boolean showImplementationDetails
  ) {
    return plugins
      .stream()
      .filter(descriptor -> !descriptor.getPluginId().equals(CORE_ID))
      .filter(descriptor -> showImplementationDetails || !descriptor.isImplementationDetail());
  }
}
