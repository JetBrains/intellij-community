// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Provides additional metadata for UI theme customization.
 * See <a href="https://www.jetbrains.org/intellij/sdk/docs/reference_guide/ui_themes/themes_metadata.html">Exposing Theme Metadata</a>.
 */
public class UIThemeMetadataProvider implements PluginAware {

  private PluginDescriptor myPluginDescriptor;

  /**
   * Path to {@code *.themeMetadata.json} file.
   */
  @Attribute("path")
  @RequiredElement
  public String path;

  @Nullable
  public UIThemeMetadata loadMetadata() {
    try {
      ClassLoader loader = myPluginDescriptor != null ? myPluginDescriptor.getPluginClassLoader() : getClass().getClassLoader();
      return UIThemeMetadata.loadFromJson(loader.getResourceAsStream(path), myPluginDescriptor.getPluginId());
    }
    catch (IOException e) {
      final String pluginId = myPluginDescriptor != null ? myPluginDescriptor.getPluginId().getIdString() : "(none)";
      Logger.getInstance(getClass()).error("error loading UIThemeMetadata '" + path + "', pluginId=" + pluginId, e);
      return null;
    }
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }
}
