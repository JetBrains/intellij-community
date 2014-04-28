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

/**
 * @author AKireyev
 */
public interface ExtensionsArea  {
  void registerExtensionPoint(@NonNls @NotNull String extensionPointName, @NotNull String extensionPointBeanClass);
  void registerExtensionPoint(@NonNls @NotNull String extensionPointName, @NotNull String extensionPointBeanClass, @NotNull ExtensionPoint.Kind kind);
  void registerExtensionPoint(@NotNull String extensionPointName, @NotNull String extensionPointBeanClass, @NotNull PluginDescriptor descriptor);
  void unregisterExtensionPoint(@NonNls @NotNull String extensionPointName);

  boolean hasExtensionPoint(@NonNls @NotNull String extensionPointName);
  @NotNull
  <T> ExtensionPoint<T> getExtensionPoint(@NonNls @NotNull String extensionPointName);

  @NotNull
  <T> ExtensionPoint<T> getExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName);

  @NotNull
  ExtensionPoint[] getExtensionPoints();
  void suspendInteractions();
  void resumeInteractions();

  void killPendingInteractions();

  void addAvailabilityListener(@NotNull String extensionPointName, @NotNull ExtensionPointAvailabilityListener listener);

  @NotNull
  AreaPicoContainer getPicoContainer();
  void registerExtensionPoint(@NotNull String pluginName, @NotNull Element extensionPointElement);
  void registerExtensionPoint(@NotNull PluginDescriptor pluginDescriptor, @NotNull Element extensionPointElement);
  void registerExtension(@NotNull String pluginName, @NotNull Element extensionElement);

  void registerExtension(@NotNull PluginDescriptor pluginDescriptor, @NotNull Element extensionElement);

  @NotNull
  PicoContainer getPluginContainer(@NotNull String pluginName);

  String getAreaClass();
}
