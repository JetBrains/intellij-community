// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.RequiredElement;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class UIThemeMetadataProvider implements PluginAware {

  private PluginDescriptor myPluginDescriptor;

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
  public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }
}
