// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.ResourceUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Function;

/**
 * Extension point for adding UI themes
 * <br/>
 * Read more about <a href=https://plugins.jetbrains.com/docs/intellij/themes.html>theme developing</a>
 *
 * @author Konstantin Bulenkov
 */
public final class UIThemeProvider implements PluginAware {
  public static final ExtensionPointName<UIThemeProvider> EP_NAME = new ExtensionPointName<>("com.intellij.themeProvider");
  private PluginDescriptor myPluginDescriptor;

  /**
   * Path to {@code *.theme.json} file
   */
  @Attribute("path")
  @RequiredElement
  public String path;

  /**
   * Unique theme identifier. For example, MyTheme123
   */
  @Attribute("id")
  @RequiredElement
  public String id;

  @ApiStatus.Internal
  public byte[] getThemeJson() throws IOException {
    @NotNull String path1 = path.charAt(0) == '/' ? path.substring(1) : path;
    return ResourceUtil.getResourceAsBytes(path1, myPluginDescriptor.getClassLoader());
  }

  public @Nullable UITheme createTheme() {
    try {
      ClassLoader classLoader = myPluginDescriptor.getPluginClassLoader();
      byte[] stream = getThemeJson();
      if (stream == null) {
        Logger.getInstance(getClass()).warn("Cannot find theme resource: " + path + " (classLoader=" + classLoader + ", pluginDescriptor=" + myPluginDescriptor + ")");
        return null;
      }
      return UITheme.loadFromJson(stream, id, classLoader, Function.identity());
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
