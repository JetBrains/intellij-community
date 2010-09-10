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

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

import java.util.List;

/**
 * @author AKireyev
 */
public interface ExtensionsArea  {
  void registerExtensionPoint(@NonNls String extensionPointName, String extensionPointBeanClass);
  void registerExtensionPoint(@NonNls String extensionPointName, String extensionPointBeanClass, ExtensionPoint.Kind kind);
  void unregisterExtensionPoint(@NonNls String extensionPointName);

  boolean hasExtensionPoint(@NonNls String extensionPointName);
  @NotNull
  <T> ExtensionPoint<T> getExtensionPoint(@NonNls String extensionPointName);
  <T> ExtensionPoint<T> getExtensionPoint(ExtensionPointName<T> extensionPointName);

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

  void registerExtensionPoint(String extensionPointName, String extensionPointBeanClass, PluginDescriptor descriptor);

  void registerAreaExtensionsAndPoints(final PluginDescriptor pluginDescriptor,
                                       final List<Element> extensionsPoints,
                                       final List<Element> extensions);
}
