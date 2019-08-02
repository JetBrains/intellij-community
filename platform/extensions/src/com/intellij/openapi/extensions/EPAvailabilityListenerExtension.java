// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @NotNull
  public Class loadListenerClass() throws ClassNotFoundException {
    if (myPluginDescriptor != null && myPluginDescriptor.getPluginClassLoader() != null) {
      return Class.forName(getListenerClass(), true, myPluginDescriptor.getPluginClassLoader());
    }
    return Class.forName(getListenerClass());
  }
}
