// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.settings;

import com.intellij.DynamicBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ResourceBundle;

/**
 * Specifies a language to be used to show code vision preview in settings for a specific model. Extension order is used to determine
 * which language to use in case of several languages are registered for the same model.
 *
 * @see CodeVisionGroupSettingModel
 */
public final class CodeVisionSettingsPreviewLanguage implements PluginAware {

  public static final ExtensionPointName<CodeVisionSettingsPreviewLanguage> EP_NAME =
    ExtensionPointName.create("com.intellij.codeInsight.codeVisionSettingsPreviewLanguage");

  private PluginDescriptor myPluginDescriptor;

  @Attribute("modelId")
  @RequiredElement
  public String modelId;

  @Attribute("language")
  @RequiredElement
  public String language;

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public @Nullable ResourceBundle findBundle() {
    String pathToBundle = myPluginDescriptor != null ? myPluginDescriptor.getResourceBundleBaseName() : null;
    if (pathToBundle == null) {
      return null;
    }
    ClassLoader loader = myPluginDescriptor != null ? myPluginDescriptor.getPluginClassLoader() : null;
    return DynamicBundle.getResourceBundle(loader != null ? loader : getClass().getClassLoader(), pathToBundle);
  }
}
