/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

import org.jdom.Element;
import org.picocontainer.PicoContainer;

/**
 * @author AKireyev
 */
public interface ExtensionsArea  {

  void registerExtensionPoint(String extensionPointName, String extensionPointBeanClass);
  void unregisterExtensionPoint(String extensionPointName);

  boolean hasExtensionPoint(String extensionPointName);
  ExtensionPoint getExtensionPoint(String extensionPointName);

  ExtensionPoint[] getExtensionPoints();

  void suspendInteractions();
  void resumeInteractions();
  void killPendingInteractions();

  void addAvailabilityListener(String epName, ExtensionPointAvailabilityListener listener);

  AreaPicoContainer getPicoContainer();

  void registerExtensionPoint(String pluginName, Element extensionPointElement);
  void registerExtensionPoint(PluginDescriptor pluginDescriptor, Element extensionPointElement);
  void registerExtension(String pluginName, Element extensionElement);
  void registerExtension(PluginDescriptor pluginDescriptor, Element extensionElement);

  void unregisterExtensionPoint(String pluginName, Element extensionPointElement);

  void unregisterExtension(String pluginName, Element extensionElement);

  PicoContainer getPluginContainer(String pluginName);

  String getAreaClass();

}
