/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

/**
 * @author Alexander Kireyev
 */
public class DefaultPluginDescriptor implements PluginDescriptor {
  private String myPluginName;
  private ClassLoader myPluginClassLoader;

  public DefaultPluginDescriptor(final String pluginName) {
    myPluginName = pluginName;
  }

  public DefaultPluginDescriptor(final String pluginName, final ClassLoader pluginClassLoader) {
    myPluginName = pluginName;
    myPluginClassLoader = pluginClassLoader;
  }

  public String getPluginName() {
    return myPluginName;
  }

  public ClassLoader getPluginClassLoader() {
    return myPluginClassLoader;
  }

  public void setPluginName(final String pluginName) {
    myPluginName = pluginName;
  }

  public void setPluginClassLoader(final ClassLoader pluginClassLoader) {
    myPluginClassLoader = pluginClassLoader;
  }
}
