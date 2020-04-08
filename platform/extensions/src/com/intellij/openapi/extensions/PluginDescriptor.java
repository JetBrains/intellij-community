// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

public interface PluginDescriptor {

  /**
   * @return plugin id or null if the descriptor is the nested (optional dependency) descriptor
   */
  PluginId getPluginId();

  ClassLoader getPluginClassLoader();

  default boolean isBundled() {
    return false;
  }

  File getPath();

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

  PluginId @NotNull [] getDependentPluginIds();

  PluginId @NotNull [] getOptionalDependentPluginIds();

  String getVendor();

  String getVersion();

  String getResourceBundleBaseName();

  String getCategory();

  @Nullable
  List<Element> getActionDescriptionElements();

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

  boolean isEnabled();

  void setEnabled(boolean enabled);

  @ApiStatus.Internal
  Disposable getPluginDisposable();
}