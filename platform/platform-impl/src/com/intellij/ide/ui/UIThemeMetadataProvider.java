// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Provides additional metadata for UI theme customization.
 * See <a href="https://www.jetbrains.org/intellij/sdk/docs/reference_guide/ui_themes/themes_metadata.html">Exposing Theme Metadata</a>.
 */
public final class UIThemeMetadataProvider implements PluginAware {
  private PluginDescriptor pluginDescriptor;

  /**
   * Path to {@code *.themeMetadata.json} file.
   */
  @Attribute("path")
  @RequiredElement
  public String path;

  @Nullable
  public UIThemeMetadata loadMetadata() {
    try {
      ClassLoader loader = pluginDescriptor == null ? getClass().getClassLoader() : pluginDescriptor.getPluginClassLoader();
      return UIThemeMetadata.loadFromJson(loader.getResourceAsStream(StringUtil.trimStart(path, "/")), pluginDescriptor.getPluginId());
    }
    catch (IOException e) {
      String pluginId = pluginDescriptor != null ? pluginDescriptor.getPluginId().getIdString() : "(none)";
      Logger.getInstance(getClass()).error("error loading UIThemeMetadata '" + path + "', pluginId=" + pluginId, e);
      return null;
    }
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}
