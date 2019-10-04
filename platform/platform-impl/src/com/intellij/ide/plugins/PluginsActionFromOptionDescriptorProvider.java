// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.ui.search.ActionFromOptionDescriptorProvider;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PluginsActionFromOptionDescriptorProvider extends ActionFromOptionDescriptorProvider {
  private static final String INSTALL_PLUGIN_FROM_DISK_BUTTON_LABEL = "Install plugin from disk...";

  @Nullable
  @Override
  public AnAction provide(@NotNull OptionDescription description) {
    String name = INSTALL_PLUGIN_FROM_DISK_BUTTON_LABEL;
    if (name.equals(description.getHit()) && "preferences.pluginManager".equals(description.getConfigurableId())) {
      return new InstallFromDiskAction(name);
    }
    return null;
  }
}
