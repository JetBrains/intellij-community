// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.newui.MyPluginModel;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public interface PluginManagerConfigurableInfo {
  @NotNull
  MyPluginModel getPluginModel();

  void select(@NotNull IdeaPluginDescriptor... descriptors);
}