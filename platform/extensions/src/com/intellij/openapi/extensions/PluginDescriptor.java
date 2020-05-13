// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Date;

public interface PluginDescriptor {
  /**
   * @return plugin id or null if the descriptor is the nested (optional dependency) descriptor
   */
  PluginId getPluginId();

  ClassLoader getPluginClassLoader();

  default boolean isBundled() {
    return false;
  }

  /**
   * @deprecated Use {@link #getPluginPath()}
   */
  @Deprecated
  default File getPath() {
    Path path = getPluginPath();
    return path == null ? null : path.toFile();
  }

  Path getPluginPath();

  @Nullable
  String getDescription();

  String getChangeNotes();

  String getName();

  @Nullable
  String getProductCode();

  @Nullable
  Date getReleaseDate();

  int getReleaseVersion();

  boolean isLicenseOptional();

  /**
   * @deprecated Do not use.
   */
  @Deprecated
  default PluginId @NotNull [] getDependentPluginIds() {
    return PluginId.EMPTY_ARRAY;
  }

  /**
   * @deprecated Do not use.
   */
  @Deprecated
  PluginId @NotNull [] getOptionalDependentPluginIds();

  String getVendor();

  String getVersion();

  String getResourceBundleBaseName();

  String getCategory();

  String getVendorEmail();

  String getVendorUrl();

  String getUrl();

  /**
   * @deprecated doesn't make sense for installed plugins; use PluginNode#getDownloads
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  default String getDownloads() {
    return null;
  }

  String getSinceBuild();

  String getUntilBuild();

  default boolean allowBundledUpdate() {
    return false;
  }

  /**
   * If true, this plugin is hidden from the list of installed plugins in Settings | Plugins.
   */
  default boolean isImplementationDetail() {
    return false;
  }

  /**
   * If true, this plugin requires restart even if it otherwise fulfills the requirements of dynamic plugins.
   */
  default boolean isRequireRestart() { return false; }

  boolean isEnabled();

  void setEnabled(boolean enabled);
}