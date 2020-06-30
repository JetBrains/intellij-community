// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;

/**
 * @author Konstantin Bulenkov
 */
public final class UIThemeProvider implements PluginAware {
  public static final ExtensionPointName<UIThemeProvider> EP_NAME = ExtensionPointName.create("com.intellij.themeProvider");
  private PluginDescriptor myPluginDescriptor;

  @Attribute("path")
  @RequiredElement
  public String path;

  @Attribute("id")
  @RequiredElement
  public String id;

  @Nullable
  public UITheme createTheme() {
    try {
      ClassLoader classLoader = myPluginDescriptor.getPluginClassLoader();
      InputStream stream = classLoader.getResourceAsStream(path.charAt(0) == '/' ? path.substring(1) : path);
      if (stream == null) {
        Logger.getInstance(getClass()).warn("Cannot find theme resource: " + path + " (classLoader=" + classLoader + ", pluginDescriptor=" + myPluginDescriptor + ")");
        return null;
      }
      return UITheme.loadFromJson(stream, id, classLoader);
    }
    catch (Exception e) {
      Logger.getInstance(getClass()).warn("error loading UITheme '" + path + "', pluginDescriptor=" + myPluginDescriptor, e);
      return null;
    }
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }
}
