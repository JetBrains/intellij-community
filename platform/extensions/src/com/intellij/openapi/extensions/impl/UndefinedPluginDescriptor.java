// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

final class UndefinedPluginDescriptor implements PluginDescriptor {
  @NotNull
  @Override
  public PluginId getPluginId() {
    throw new UnsupportedOperationException("This method should not be called on this object");
  }

  @Override
  public ClassLoader getPluginClassLoader() {
    return null;
  }
}
