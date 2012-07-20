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
  private String myClasspath;

  /**
   * Specifies semicolon-separated list of paths which should be added to the classpath of the compile server. The paths are relative to the plugin 'lib' directory.
   * <p>
   * In the development mode the name of each file without extension is treated as a module name and the output directory of the module
   * is added to the classpath. If such file doesn't exists the jar is searched under 'lib' directory of the plugin sources home directory.
   * </p>
   */
  @Attribute("classpath")
  public String getClasspath() {
    return myClasspath;
  }

  public void setClasspath(String classpath) {
    myClasspath = classpath;
  }

  @Override
  public final void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }
}
