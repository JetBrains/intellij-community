/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ide.plugins;

import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author max
 */
public interface IdeaPluginDescriptor extends PluginDescriptor {
  File getPath();

  String getDescription();

  String getChangeNotes();

  String getName();

  @NotNull
  PluginId[] getDependentPluginIds();

  @NotNull
  PluginId[] getOptionalDependentPluginIds();

  String getVendor();

  String getVersion();

  String getResourceBundleBaseName();

  String getCategory();

  Element getActionsDescriptionElement();

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

  String getDownloads();
}
