/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

/**
 * @author Alexander Kireyev
 */
public class DefaultPluginDescriptor implements PluginDescriptor {
  private PluginId myPluginId;
  private ClassLoader myPluginClassLoader;

  public DefaultPluginDescriptor(String pluginId) {
    myPluginId = PluginId.getId(pluginId);
  }

  public DefaultPluginDescriptor(final PluginId pluginId) {
    myPluginId = pluginId;
  }

  public DefaultPluginDescriptor(final PluginId pluginId, final ClassLoader pluginClassLoader) {
    myPluginId = pluginId;
    myPluginClassLoader = pluginClassLoader;
  }

  public PluginId getPluginId() {
    return myPluginId;
  }

  public ClassLoader getPluginClassLoader() {
    return myPluginClassLoader;
  }

  public void setPluginId(final PluginId pluginId) {
    myPluginId = pluginId;
  }

  public void setPluginClassLoader(final ClassLoader pluginClassLoader) {
    myPluginClassLoader = pluginClassLoader;
  }
}
