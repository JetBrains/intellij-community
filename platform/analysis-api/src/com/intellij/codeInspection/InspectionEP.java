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
package com.intellij.codeInspection;

import com.intellij.AbstractBundle;
import com.intellij.CommonBundle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ResourceBundle;

/**
 * @author Dmitry Avdeev
 * @since 27.09.2011
 * @see LocalInspectionEP
 */
public class InspectionEP extends LanguageExtensionPoint implements InspectionProfileEntry.DefaultNameProvider {

  /** @see GlobalInspectionTool */
  public static final ExtensionPointName<InspectionEP> GLOBAL_INSPECTION = ExtensionPointName.create("com.intellij.globalInspection");

  /**
   * Short name is used in two cases: \inspectionDescriptions\&lt;short_name&gt;.html resource may contain short inspection
   * description to be shown in "Inspect Code..." dialog and also provide some file name convention when using offline
   * inspection or export to HTML function. Should be unique among all inspections.
   */
  @Attribute("shortName")
  public String shortName;

  @NotNull
  public String getShortName() {
    if (implementationClass == null) {
      throw new IllegalArgumentException(toString());
    }
    return shortName == null ? InspectionProfileEntry.getShortName(StringUtil.getShortName(implementationClass)) : shortName;
  }

  @Nullable
  @Nls
  public String getDisplayName() {
    return displayName == null ? displayName = getLocalizedString(bundle, key) : displayName;
  }

  @Nullable
  @Nls
  public String getGroupDisplayName() {
    return groupDisplayName == null ? groupDisplayName = getLocalizedString(groupBundle, groupKey) : groupDisplayName;
  }

  @Attribute("key")
  public String key;

  @Attribute("bundle")
  public String bundle;

  @Attribute("displayName")
  public String displayName;

  @Attribute("groupKey")
  public String groupKey;

  @Attribute("groupBundle")
  public String groupBundle;

  @Attribute("groupName")
  public String groupDisplayName;

  /** Comma-delimited list of parent groups (excluding groupName)*/
  @Attribute("groupPath")
  public String groupPath;

  @Nullable
  public String[] getGroupPath() {
    String name = getGroupDisplayName();
    if (name == null) return null;
    if (groupPath == null) {
      return new String[]{name.isEmpty() ? InspectionProfileEntry.GENERAL_GROUP_NAME : name};
    }
    return ArrayUtil.append(groupPath.split(","), name);
  }

  @Attribute("enabledByDefault")
  public boolean enabledByDefault = false;

  @Attribute("applyToDialects")
  public boolean applyToDialects = true;

  /**
   * If true, the inspection can run as part of the code cleanup action.
   */
  @Attribute("cleanupTool")
  public boolean cleanupTool = false;

  /**
   * Highlighting level for this inspection tool that is used in default settings.
   */
  @Attribute("level")
  public String level;

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    if (level == null) return HighlightDisplayLevel.WARNING;
    HighlightDisplayLevel displayLevel = HighlightDisplayLevel.find(level);
    if (displayLevel == null) {
      LOG.error("Can't find highlight display level: " + level);
      return HighlightDisplayLevel.WARNING;
    }
    return displayLevel;
  }

  @Attribute("hasStaticDescription")
  public boolean hasStaticDescription;

  @Nullable
  private String getLocalizedString(String bundleName, String key) {
    final String baseName = bundleName != null ? bundleName : bundle == null ? ((IdeaPluginDescriptor)myPluginDescriptor).getResourceBundleBaseName() : bundle;
    if (baseName == null || key == null) {
      if (bundleName != null) {
        LOG.warn(implementationClass);
      }
      return null;
    }
    final ResourceBundle resourceBundle = AbstractBundle.getResourceBundle(baseName, myPluginDescriptor.getPluginClassLoader());
    return CommonBundle.message(resourceBundle, key);
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.InspectionEP");

  @NotNull
  public InspectionProfileEntry instantiateTool() {
    try {
      final InspectionProfileEntry entry = instantiate(implementationClass, ApplicationManager.getApplication().getPicoContainer());
      entry.myNameProvider = this;
      return entry;
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getDefaultShortName() {
    return getShortName();
  }

  @Override
  public String getDefaultDisplayName() {
    return getDisplayName();
  }

  @Override
  public String getDefaultGroupDisplayName() {
    return getGroupDisplayName();
  }

  @Attribute("presentation")
  public String presentation;

  /**
   * Do not show internal inspections if internal mode is off
   */
  @Attribute("isInternal") 
  public boolean isInternal = false;

  @Override
  public String toString() {
    return "InspectionEP{" +
           "shortName='" + shortName + '\'' +
           ", key='" + key + '\'' +
           ", bundle='" + bundle + '\'' +
           ", displayName='" + displayName + '\'' +
           ", groupKey='" + groupKey + '\'' +
           ", groupBundle='" + groupBundle + '\'' +
           ", groupDisplayName='" + groupDisplayName + '\'' +
           ", groupPath='" + groupPath + '\'' +
           ", enabledByDefault=" + enabledByDefault +
           ", applyToDialects=" + applyToDialects +
           ", cleanupTool=" + cleanupTool +
           ", level='" + level + '\'' +
           ", hasStaticDescription=" + hasStaticDescription +
           ", presentation='" + presentation + '\'' +
           ", isInternal=" + isInternal +
           '}';
  }
}
