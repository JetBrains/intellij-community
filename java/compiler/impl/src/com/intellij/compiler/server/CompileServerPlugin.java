/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
   * <p>Specifies semicolon-separated list of paths which should be added to the classpath of the compile server.
   * The paths are relative to the plugin 'lib' directory.</p>
   *
   * <p>In the development mode the name of each file without extension is treated as a module name and the output directory of the module
   * is added to the classpath. If such file doesn't exists the jar is searched under 'lib' directory of the plugin sources home directory.</p>
   */
  @Attribute("classpath")
  public String getClasspath() {
    return myClasspath;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setClasspath(String classpath) {
    myClasspath = classpath;
  }

  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @Override
  public final void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }
}
