/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.plugins;

import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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

  String getDownloads();

  String getSinceBuild();

  String getUntilBuild();

  boolean isBundled();
  boolean allowBundledUpdate();

  boolean isEnabled();
  void setEnabled(boolean enabled);
}
