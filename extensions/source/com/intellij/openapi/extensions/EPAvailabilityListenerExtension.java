/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

/**
 * @author AKireyev
 */
public class EPAvailabilityListenerExtension {
  public static final String EXTENSION_POINT_NAME = "jetbrains.fabrique.framework.epAvailabilityListener";

  private String myExtensionPointName;
  private String myListenerClass;

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
}
