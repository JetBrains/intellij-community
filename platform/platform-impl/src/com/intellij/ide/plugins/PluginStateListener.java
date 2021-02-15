// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public interface PluginStateListener {
  void install(@NotNull IdeaPluginDescriptor descriptor);

  default void uninstall(@NotNull IdeaPluginDescriptor descriptor) {
  }
}