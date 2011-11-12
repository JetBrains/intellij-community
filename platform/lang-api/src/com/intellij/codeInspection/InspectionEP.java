/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ResourceBundle;

/**
 * @author Dmitry Avdeev
 *         Date: 9/27/11
 * @see LocalInspectionEP
 */
public class InspectionEP extends LanguageExtensionPoint {

  /** @see GlobalInspectionTool */
  public final static ExtensionPointName<InspectionEP> GLOBAL_INSPECTION = ExtensionPointName.create("com.intellij.globalInspection");

  @Attribute("shortName")
  public String shortName;
  @Nls
  @NotNull
  public String getDisplayName() {
    return getLocalizedString(bundle, key, displayName);
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return getLocalizedString(groupBundle, groupKey, groupDisplayName);
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

  @Attribute("enabledByDefault")
  public boolean enabledByDefault = false;

  @Attribute("applyToDialects")
  public boolean applyToDialects = true;

  @Attribute("level")
  public String level;

  public HighlightDisplayLevel getDefaultLevel() {
    HighlightDisplayLevel displayLevel = HighlightDisplayLevel.find(level);
    if (displayLevel == null) {
      LOG.error("Can't find highlight display level: " + level);
      return HighlightDisplayLevel.WARNING;
    }
    return displayLevel;
  }
  
  private String getLocalizedString(String bundleName, String key, String displayName) {
    if (displayName != null) return displayName;
    final String baseName = bundleName != null ? bundleName : bundle == null ? ((IdeaPluginDescriptor)myPluginDescriptor).getResourceBundleBaseName() : bundle;
    if (baseName == null) {
      LOG.error("No resource bundle specified for " + myPluginDescriptor + " Inspection: " + implementationClass);
    }
    final ResourceBundle bundle = AbstractBundle.getResourceBundle(baseName, myPluginDescriptor.getPluginClassLoader());
    return CommonBundle.message(bundle, key);
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.InspectionEP");

  public InspectionProfileEntry instantiateTool() {
    try {
      return instantiate(implementationClass, ApplicationManager.getApplication().getPicoContainer());
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
    
}
