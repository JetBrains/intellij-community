/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.components;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ComponentConfig {
  public static final ComponentConfig[] EMPTY_ARRAY = new ComponentConfig[0];

  protected String implementationClass;

  protected String interfaceClass;

  protected String headlessImplementationClass;

  protected boolean loadForDefaultProject;

  @Property(surroundWithTag = false)
  @MapAnnotation(surroundWithTag = false, entryTagName = "option", keyAttributeName = "name", valueAttributeName = "value")
  @Nullable
  public Map<String, String> options;

  @Transient
  public PluginDescriptor pluginDescriptor;

  @Transient
  public ClassLoader getClassLoader() {
    return pluginDescriptor != null ? pluginDescriptor.getPluginClassLoader() : getClass().getClassLoader();
  }

  @Transient
  public PluginId getPluginId() {
    return pluginDescriptor == null ? null : pluginDescriptor.getPluginId();
  }

  public String getImplementationClass() {
    return implementationClass;
  }

  public String getInterfaceClass() {
    return interfaceClass;
  }

  @SuppressWarnings("UnusedDeclaration")
  public String getHeadlessImplementationClass() {
    return headlessImplementationClass;
  }

  public boolean isLoadForDefaultProject() {
    return loadForDefaultProject;
  }

  /**
   * @return false if the component should not be loaded in headless mode
   */
  public boolean prepareClasses(boolean headless) {
    if (headless && headlessImplementationClass != null) {
      if (StringUtil.isEmpty(headlessImplementationClass)) return false;
      setImplementationClass(headlessImplementationClass);
    }
    if (StringUtil.isEmpty(interfaceClass)) {
      setInterfaceClass(implementationClass);
    }
    return true;
  }

  public void setImplementationClass(String implementationClass) {
    this.implementationClass = implementationClass == null ? null : implementationClass.trim();
  }

  public void setInterfaceClass(String interfaceClass) {
    this.interfaceClass = interfaceClass == null ? null : interfaceClass.trim();
  }

  public void setHeadlessImplementationClass(String headlessImplementationClass) {
    headlessImplementationClass = headlessImplementationClass == null ? null : headlessImplementationClass.trim();
    this.headlessImplementationClass = headlessImplementationClass == null ? null : StringUtil.isEmpty(headlessImplementationClass) ? "" : headlessImplementationClass;
  }

  public void setLoadForDefaultProject(boolean loadForDefaultProject) {
    this.loadForDefaultProject = loadForDefaultProject;
  }

  @Override
  public String toString() {
    return "ComponentConfig{" +
           "implementationClass='" + implementationClass + '\'' +
           ", interfaceClass='" + interfaceClass + '\'' +
           ", headlessImplementationClass='" + headlessImplementationClass + '\'' +
           ", loadForDefaultProject=" + loadForDefaultProject +
           ", options=" + options +
           ", pluginDescriptor=" + pluginDescriptor +
           '}';
  }
}
