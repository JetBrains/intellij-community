package com.intellij.openapi.samples;


import com.intellij.openapi.components.ApplicationComponent;

/**
 * <h3>SampleApplicationPlugin</h3>
 * Application level plugin sample showing IDEA <b>OpenAPI</b> basics.<br>
 * Implements <code>ApplicationComponent</code> interface.
 */
public class SampleApplicationPlugin implements ApplicationComponent {
  /**
   * Method is called after plugin is already created and configured. Plugin can start to communicate with
   * other plugins only in this method.
   */
  public void initComponent() { System.out.println("SampleApplicationPlugin: initComponent"); }

  /**
   * This method is called on plugin disposal.
   */
  public void disposeComponent() {
    System.out.println("SampleApplicationPlugin: disposeComponent");
  }

  /**
   * Returns the name of component
   * @return String representing component name. Use plugin_name.component_name notation.
   */
  public String getComponentName() {
    return "Sample.SampleApplicationPlugin";
  }
}
