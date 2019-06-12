// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.RequiredElement;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public final class UIThemeProvider implements PluginAware {
  public static final ExtensionPointName<UIThemeProvider> EP_NAME = ExtensionPointName.create("com.intellij.themeProvider");
  private PluginDescriptor myDescriptor;

  @Attribute("path")
  @RequiredElement
  public String path;

  @Attribute("id")
  @RequiredElement
  public String id;

  @Nullable
  public UITheme createTheme() {
    try {
      ClassLoader loader = myDescriptor != null ? myDescriptor.getPluginClassLoader() : getClass().getClassLoader();
      return UITheme.loadFromJson(loader.getResourceAsStream(path), id, loader);
    }
    catch (Exception e) {
      Logger.getInstance(getClass()).warn("error loading UITheme '" + path + "', " +
                                          "pluginId=" + (myDescriptor != null ? myDescriptor.getPluginId().getIdString() : "(none)"), e);
      return null;
    }
  }

  @Override
  public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myDescriptor = pluginDescriptor;
  }
}
