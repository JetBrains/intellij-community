/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;

/**
 * @author max
 */
public class RootPluginDescriptor implements PluginDescriptor {
  public final static RootPluginDescriptor INSTANCE = new RootPluginDescriptor();
  private RootPluginDescriptor() {}

  public PluginId getPluginId() {
    return PluginId.getId("com.intellij");
  }

  public ClassLoader getPluginClassLoader() {
    return getClass().getClassLoader();
  }
}
