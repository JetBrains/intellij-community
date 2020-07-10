// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public interface ExtensionsArea  {
  /**
   * @deprecated use {@link ExtensionsArea#registerExtensionPoint(String, String, ExtensionPoint.Kind)}
   */
  @TestOnly
  @Deprecated
  default void registerExtensionPoint(@NonNls @NotNull String extensionPointName, @NotNull String extensionPointBeanClass) {
    registerExtensionPoint(extensionPointName, extensionPointBeanClass, ExtensionPoint.Kind.INTERFACE);
  }

  @TestOnly
  void registerExtensionPoint(@NonNls @NotNull String extensionPointName, @NotNull String extensionPointBeanClass, @NotNull ExtensionPoint.Kind kind);

  void unregisterExtensionPoint(@NonNls @NotNull String extensionPointName);

  boolean hasExtensionPoint(@NonNls @NotNull String extensionPointName);

  boolean hasExtensionPoint(@NotNull ExtensionPointName<?> extensionPointName);

  @NotNull
  <T> ExtensionPoint<T> getExtensionPoint(@NonNls @NotNull String extensionPointName);

  @Nullable
  <T> ExtensionPoint<T> getExtensionPointIfRegistered(@NotNull String extensionPointName);

  @NotNull
  <T> ExtensionPoint<T> getExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName);

  @NotNull List<ExtensionPoint<?>> getExtensionPoints();

  /**
   * Registers a new extension.
   * @param pluginDescriptor plugin to which extension belongs
   * @param extensionElement element from plugin.xml file where extension settings are specified
   * @param extensionNs extension namespace which is prepended to the tag name from {@code extensionElement} to form the qualified extension name.
   */
  @TestOnly
  void registerExtension(@NotNull PluginDescriptor pluginDescriptor, @NotNull Element extensionElement, @Nullable String extensionNs);

  /**
   * @deprecated Do not use.
   */
  @Deprecated
  void registerExtension(@NotNull ExtensionPoint<?> extensionPoint, @NotNull PluginDescriptor pluginDescriptor, @NotNull Element extensionElement);
}
