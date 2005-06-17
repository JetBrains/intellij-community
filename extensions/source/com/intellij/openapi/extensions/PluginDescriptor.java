/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

/**
 * @author Alexander Kireyev
 */
public interface PluginDescriptor {
  PluginId getPluginId();
  ClassLoader getPluginClassLoader();
}
