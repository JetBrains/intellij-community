/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Mar 4, 2005
 */
public abstract class JUnitPatcher implements PluginAware {
  private PluginDescriptor myPlugin;

  /**
   * Called by the framework. Allows to identify the plugin that provided this extension.
   * @param plugin
   */
  public void setPluginDescriptor(PluginDescriptor plugin) {
    myPlugin = plugin;
  }

  /**
   * @return plugin that provided this particular extension
   */
  public PluginDescriptor getPluginDescriptor() {
    return myPlugin;
  }

  /**
   * @deprecated override {@link #patchJavaParameters(Module, JavaParameters)} instead
   */
  public void patchJavaParameters(JavaParameters javaParameters) {
  }

  public void patchJavaParameters(@Nullable Module module, JavaParameters javaParameters) {
    patchJavaParameters(javaParameters);
  }
}
