package com.intellij.compiler.server;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author nik
 */
public class CompileServerPlugin implements PluginAware {
  public static final ExtensionPointName<CompileServerPlugin> EP_NAME = ExtensionPointName.create("com.intellij.compileServer.plugin");
  private PluginDescriptor myPluginDescriptor;
  private String myJarPath;

  /**
   * Specifies path to a jar file which should be added to the classpath of the compile server. The path is relative to the plugin 'lib' directory.
   * In the development mode the name of this file without extension is treated as a module name and the output directory of the module is added to the classpath.
   */
  @Attribute("jar-path")
  public String getJarPath() {
    return myJarPath;
  }

  public void setJarPath(String jarPath) {
    myJarPath = jarPath;
  }

  @Override
  public final void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }
}
