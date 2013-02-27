/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;

/**
 * @author AKireyev
 */
public class EPAvailabilityListenerExtension implements PluginAware {
  public static final String EXTENSION_POINT_NAME = "com.intellij.openapi.extensions.epAvailabilityListener";

  private String myExtensionPointName;
  private String myListenerClass;
  private PluginDescriptor myPluginDescriptor;

  public EPAvailabilityListenerExtension() {
  }

  public EPAvailabilityListenerExtension(@NotNull String extensionPointName, @NotNull String listenerClass) {
    myExtensionPointName = extensionPointName;
    myListenerClass = listenerClass;
  }

  @NotNull
  public String getExtensionPointName() {
    return myExtensionPointName;
  }

  public void setExtensionPointName(@NotNull String extensionPointName) {
    myExtensionPointName = extensionPointName;
  }

  @NotNull
  public String getListenerClass() {
    return myListenerClass;
  }

  public void setListenerClass(@NotNull String listenerClass) {
    myListenerClass = listenerClass;
  }

  @Override
  public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  public Class loadListenerClass() throws ClassNotFoundException {
    if (myPluginDescriptor != null && myPluginDescriptor.getPluginClassLoader() != null) {
      return Class.forName(getListenerClass(), true, myPluginDescriptor.getPluginClassLoader());
    }
    else {
      return Class.forName(getListenerClass());
    }
  }
}
