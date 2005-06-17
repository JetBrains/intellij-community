/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;

/**
 * @author Alexander Kireyev
 */
class UndefinedPluginDescriptor implements PluginDescriptor {
  public PluginId getPluginId() {
    throw new UnsupportedOperationException("This method should not be called on this object");
  }

  public ClassLoader getPluginClassLoader() {
    return null;
  }
}
