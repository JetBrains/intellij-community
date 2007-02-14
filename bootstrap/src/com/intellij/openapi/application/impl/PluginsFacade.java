/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.application.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.extensions.PluginId;

/**
 * @author max
 */
public abstract class PluginsFacade {
  public static PluginsFacade INSTANCE;

  public abstract IdeaPluginDescriptor getPlugin(PluginId id);
  public abstract IdeaPluginDescriptor[] getPlugins();
}
