/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jdom.Element;

import java.io.File;

/**
 * @author max
 */
public interface IdeaPluginDescriptor extends PluginDescriptor {
  File getPath();

  String getDescription();

  String getChangeNotes();

  String getName();

  PluginId[] getDependentPluginIds();

  PluginId[] getOptionalDependentPluginIds();

  String getVendor();

  String getVersion();

  String getResourceBundleBaseName();

  String getCategory();

  Element getActionsDescriptionElement();

  Element getAppComponents();

  Element getProjectComponents();

  Element getModuleComponents();

  String getVendorEmail();

  String getVendorUrl();

  String getUrl();

  HelpSetPath[] getHelpSets();

  String getVendorLogoPath();

  boolean getUseIdeaClassLoader();
}
