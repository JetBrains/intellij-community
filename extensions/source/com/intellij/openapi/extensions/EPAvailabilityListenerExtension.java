/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

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

  public EPAvailabilityListenerExtension(String extensionPointName, String listenerClass) {
    myExtensionPointName = extensionPointName;
    myListenerClass = listenerClass;
  }

  public String getExtensionPointName() {
    return myExtensionPointName;
  }

  public void setExtensionPointName(String extensionPointName) {
    myExtensionPointName = extensionPointName;
  }

  public String getListenerClass() {
    return myListenerClass;
  }

  public void setListenerClass(String listenerClass) {
    myListenerClass = listenerClass;
  }

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
