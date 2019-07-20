// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.MutablePicoContainer;

/**
 * @see Extensions#getArea(AreaInstance)
 * @see Extensions#getRootArea()
 */
public interface ExtensionsArea  {
  @TestOnly
  void registerExtensionPoint(@NotNull BaseExtensionPointName extensionPoint,
                              @NotNull String extensionPointBeanClass,
                              @NotNull ExtensionPoint.Kind kind,
                              @NotNull Disposable parentDisposable);

  /**
   * @deprecated use {@link ExtensionsArea#registerExtensionPoint(BaseExtensionPointName, String, ExtensionPoint.Kind, Disposable)}
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

  @NotNull
  ExtensionPoint[] getExtensionPoints();

  void addAvailabilityListener(@NotNull String extensionPointName, @NotNull ExtensionPointAvailabilityListener listener, @Nullable Disposable parentDisposable);

  /**
   * @deprecated use {@link #addAvailabilityListener(String, ExtensionPointAvailabilityListener, Disposable)}
   */
  @Deprecated
  default void addAvailabilityListener(@NotNull String extensionPointName, @NotNull ExtensionPointAvailabilityListener listener) {
    addAvailabilityListener(extensionPointName, listener, null);
  }

  /**
   * @deprecated use {@link #addAvailabilityListener(String, ExtensionPointAvailabilityListener, Disposable)}
   */
  @Deprecated
  void removeAvailabilityListener(@NotNull String extensionPointName, @NotNull ExtensionPointAvailabilityListener listener);

  @NotNull
  MutablePicoContainer getPicoContainer();

  void registerExtensionPoint(@NotNull PluginDescriptor pluginDescriptor, @NotNull Element extensionPointElement);

  /**
   * Registers a new extension.
   * @param pluginDescriptor plugin to which extension belongs
   * @param extensionElement element from plugin.xml file where extension settings are specified
   * @param extensionNs extension namespace which is prepended to the tag name from {@code extensionElement} to form the qualified extension name.
   */
  @TestOnly
  void registerExtension(@NotNull PluginDescriptor pluginDescriptor, @NotNull Element extensionElement, @Nullable String extensionNs);

  void registerExtension(@NotNull final ExtensionPoint extensionPoint, @NotNull final PluginDescriptor pluginDescriptor, @NotNull final Element extensionElement);

  String getAreaClass();
}
