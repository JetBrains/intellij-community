package com.intellij.openapi.samples;

import com.intellij.openapi.components.ApplicationComponent;

/**
 * <h3>SampleApplicationPlugin</h3>
 *
 * Application level plugin sample showing IDEA <b>OpenAPI</b> basics.<br>
 * Implements <code>ApplicationComponent</code> interface.
 *
 */
public class ActionsPlugin implements ApplicationComponent {

  /**
   * Method is called after plugin is already created and configured. Plugin can start to communicate with
   * other plugins only in this method.
   */
  public void initComponent() { }

  /**
   * This method is called on plugin disposal.
   */
  public void disposeComponent() {
  }

  /**
   * Returns the name of component
   *
   * @return String representing component name. Use PluginName.ComponentName notation
   *  to avoid conflicts.
   */
  public String getComponentName() {
    return "ActionsSample.ActionsPlugin";
  }
}
