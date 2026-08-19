// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension that allows patching the command line parameters
 * when running JUnit test configuration (e.g., tune classpath, add system properties, etc.).
 * This allows better integration with custom project models like Maven.
 * <p>
 * All registered extensions are applied when JUnit test configuration is started.
 */
public abstract class JUnitPatcher implements PluginAware {
  private PluginDescriptor myPlugin;

  @ApiStatus.Internal
  public static final ExtensionPointName<JUnitPatcher> JUNIT_PATCHER_EP = new ExtensionPointName<>("com.intellij.junitPatcher");

  /**
   * Called by the framework. Allows to identify the plugin that provided this extension.
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

  /**
   * Patches JavaParameters used to launch junit
   *
   * @param project current project
   * @param module starting module
   * @param javaParameters java parameters object that can be patched if necessary
   */
  public void patchJavaParameters(@NotNull Project project, @Nullable Module module, JavaParameters javaParameters) {
    patchJavaParameters(module, javaParameters);
  }

  /**
   * Patches JavaParameters used to launch junit
   *
   * @param module starting module
   * @param javaParameters java parameters object that can be patched if necessary
   */
  public void patchJavaParameters(@Nullable Module module, JavaParameters javaParameters) {
  }
}
