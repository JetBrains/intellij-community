// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Date;

public interface PluginDescriptor {
  @NotNull PluginId getPluginId();

  @Nullable ClassLoader getPluginClassLoader();

  @ApiStatus.Experimental
  default @NotNull ClassLoader getClassLoader() {
    ClassLoader classLoader = getPluginClassLoader();
    return classLoader == null ? getClass().getClassLoader() : classLoader;
  }

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

  @Nullable @Nls String getDescription();

  @Nullable String getChangeNotes();

  @NlsSafe String getName();

  @Nullable String getProductCode();

  @Nullable Date getReleaseDate();

  int getReleaseVersion();

  boolean isLicenseOptional();

  /**
   * @deprecated Do not use.
   */
  @Deprecated
  PluginId @NotNull [] getOptionalDependentPluginIds();

  @Nullable @NlsSafe String getVendor();

  //TODO: remove default implementation in 2021.3
  default @Nullable @NlsSafe String getOrganization() {
    return null;
  }

  @NlsSafe String getVersion();

  @Nullable String getResourceBundleBaseName();

  @Nullable @NlsSafe String getCategory();

  @Nullable String getVendorEmail();

  @Nullable String getVendorUrl();

  @Nullable String getUrl();

  /**
   * @deprecated doesn't make sense for installed plugins; use PluginNode#getDownloads
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  default @Nullable String getDownloads() {
    return null;
  }

  @Nullable @NlsSafe String getSinceBuild();

  @Nullable @NlsSafe String getUntilBuild();

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