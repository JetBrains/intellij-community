package com.intellij.execution;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;

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

  public abstract void patchJavaParameters(JavaParameters javaParameters);
}
