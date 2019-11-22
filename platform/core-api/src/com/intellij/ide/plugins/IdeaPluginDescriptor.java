// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * Describes a plugin which may be installed into IntelliJ-based IDE. Use {@link com.intellij.ide.plugins.PluginManager#getPlugin(PluginId)}
 * to get a descriptor by a plugin ID and {@link com.intellij.ide.plugins.PluginManagerCore#getPlugins()} to get all plugins.
 */
public interface IdeaPluginDescriptor extends PluginDescriptor {
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

  String getVendorLogoPath();

  boolean getUseIdeaClassLoader();

  /** @deprecated doesn't make sense for installed plugins; use PluginNode#getDownloads */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  default String getDownloads() {
    return null;
  }

  String getSinceBuild();

  String getUntilBuild();

  boolean allowBundledUpdate();

  /**
   * If true, this plugin is hidden from the list of installed plugins in Settings | Plugins.
   */
  boolean isImplementationDetail();

  boolean isEnabled();
  void setEnabled(boolean enabled);
}