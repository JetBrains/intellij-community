// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public final class IconMapperBean implements PluginAware {
  public static final ExtensionPointName<IconMapperBean> EP_NAME = new ExtensionPointName<>("com.intellij.iconMapper");
  private ClassLoader pluginClassLoader;

  @Attribute("mappingFile")
  @RequiredElement
  public @NonNls String mappingFile;

  public ClassLoader getPluginClassLoader() {
    return pluginClassLoader;
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    pluginClassLoader = pluginDescriptor.getPluginClassLoader();
  }
}
