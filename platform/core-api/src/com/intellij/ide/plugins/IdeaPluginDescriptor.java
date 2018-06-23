// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * @author max
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
  List<Element> getActionsDescriptionElements();

  @NotNull
  ComponentConfig[] getAppComponents();

  @NotNull
  ComponentConfig[] getProjectComponents();

  @NotNull
  ComponentConfig[] getModuleComponents();

  String getVendorEmail();

  String getVendorUrl();

  String getUrl();

  @NotNull
  HelpSetPath[] getHelpSets();

  String getVendorLogoPath();

  boolean getUseIdeaClassLoader();

  /** @deprecated doesn't make sense for installed plugins; use PluginNode#getDownloads (to be removed in IDEA 2019) */
  @Deprecated
  String getDownloads();

  String getSinceBuild();

  String getUntilBuild();

  boolean isBundled();
  boolean allowBundledUpdate();

  boolean isEnabled();
  void setEnabled(boolean enabled);
}