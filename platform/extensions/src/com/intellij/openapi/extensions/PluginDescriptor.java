// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Date;
import java.util.List;

public interface PluginDescriptor {
  PluginId getPluginId();

  ClassLoader getPluginClassLoader();

  default boolean isBundled() {
    return false;
  }

  File getPath();

  @Nullable
  String getDescription();

  String getChangeNotes();

  String getName();

  @Nullable
  String getProductCode();

  @Nullable
  Date getReleaseDate();

  int getReleaseVersion();

  @NotNull
  PluginId[] getDependentPluginIds();

  @NotNull
  PluginId[] getOptionalDependentPluginIds();

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
}