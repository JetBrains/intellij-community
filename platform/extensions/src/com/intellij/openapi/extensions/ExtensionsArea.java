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
import org.jetbrains.annotations.Nullable;

/**
 * @see Extensions#getArea(AreaInstance)
 * @see Extensions#getRootArea()
 */
public interface ExtensionsArea  {
  void registerExtensionPoint(@NonNls @NotNull String extensionPointName, @NotNull String extensionPointBeanClass);
  void registerExtensionPoint(@NonNls @NotNull String extensionPointName, @NotNull String extensionPointBeanClass, @NotNull ExtensionPoint.Kind kind);
  void unregisterExtensionPoint(@NonNls @NotNull String extensionPointName);

  boolean hasExtensionPoint(@NonNls @NotNull String extensionPointName);
  @NotNull
  <T> ExtensionPoint<T> getExtensionPoint(@NonNls @NotNull String extensionPointName);

  @NotNull
  <T> ExtensionPoint<T> getExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName);

  @NotNull
  ExtensionPoint[] getExtensionPoints();

  void addAvailabilityListener(@NotNull String extensionPointName, @NotNull ExtensionPointAvailabilityListener listener);

  @NotNull
  AreaPicoContainer getPicoContainer();
  void registerExtensionPoint(@NotNull PluginDescriptor pluginDescriptor, @NotNull Element extensionPointElement);

  /**
   * Registers a new extension.
   * @param pluginDescriptor plugin to which extension belongs
   * @param extensionElement element from plugin.xml file where extension settings are specified
   * @param extensionNs extension namespace which is prepended to the tag name from {@code extensionElement} to form the qualified extension name.
   */
  void registerExtension(@NotNull PluginDescriptor pluginDescriptor, @NotNull Element extensionElement, @Nullable String extensionNs);

  void registerExtension(@NotNull final ExtensionPoint extensionPoint, @NotNull final PluginDescriptor pluginDescriptor, @NotNull final Element extensionElement);

  String getAreaClass();
}
