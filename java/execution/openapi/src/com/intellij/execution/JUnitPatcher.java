// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JUnitPatcher implements PluginAware {
  private PluginDescriptor myPlugin;

  /**
   * Called by the framework. Allows to identify the plugin that provided this extension.
   * @param plugin
   */
  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor plugin) {
    myPlugin = plugin;
  }

  /**
   * @return plugin that provided this particular extension
   */
  public PluginDescriptor getPluginDescriptor() {
    return myPlugin;
  }

  public void patchJavaParameters(@NotNull Project project, @Nullable Module module, JavaParameters javaParameters) {
    patchJavaParameters(module, javaParameters);
  }

  public void patchJavaParameters(@Nullable Module module, JavaParameters javaParameters) {
  }
}
