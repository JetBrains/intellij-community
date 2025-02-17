// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.extensionResources;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ExternalResourcesUnpackExtensionBean implements PluginAware {
  private static final ExtensionPointName<ExternalResourcesUnpackExtensionBean> EP = ExtensionPointName.create("com.intellij.pluginExternalResources.unpackToPlugin");

  @Attribute("unpackTo")
  public String unpackTo;

  private PluginDescriptor pluginDescriptor;

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }

  public PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  public static List<ExternalResourcesUnpackExtensionBean> allPluginsWithExtension() {
    return EP.getExtensionList();
  }

  public static List<ExternalResourcesUnpackExtensionBean> getPluginsBeUnpackedTo(@NotNull PluginId pluginId) {
    String id = pluginId.getIdString();
    return ContainerUtil.filter(allPluginsWithExtension(), unpackExtensionBean -> unpackExtensionBean.unpackTo.equals(id));
  }

  public static List<ExternalResourcesUnpackExtensionBean> getPluginBeans(@NotNull PluginId pluginId) {
    return ContainerUtil.filter(allPluginsWithExtension(), bean -> bean.pluginDescriptor.getPluginId().equals(pluginId));
  }
}
