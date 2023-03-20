// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public interface ExtensionsArea {
  @TestOnly
  void registerExtensionPoint(@NonNls @NotNull String extensionPointName,
                              @NotNull String extensionPointBeanClass,
                              @NotNull ExtensionPoint.Kind kind,
                              boolean isDynamic);

  /**
   * @deprecated Use {@link #registerExtensionPoint(String, String, ExtensionPoint.Kind, boolean)}
   */
  @TestOnly
  @Deprecated
  default void registerExtensionPoint(@NonNls @NotNull String extensionPointName,
                                      @NotNull String extensionPointBeanClass,
                                      @NotNull ExtensionPoint.Kind kind) {
    registerExtensionPoint(extensionPointName, extensionPointBeanClass, kind, false);
  }

  @TestOnly
  void unregisterExtensionPoint(@NonNls @NotNull String extensionPointName);

  boolean hasExtensionPoint(@NonNls @NotNull String extensionPointName);

  boolean hasExtensionPoint(@NotNull ExtensionPointName<?> extensionPointName);

  @NotNull <T> ExtensionPoint<@NotNull T> getExtensionPoint(@NonNls @NotNull String extensionPointName);

  <T> ExtensionPoint<@NotNull T> getExtensionPointIfRegistered(@NotNull String extensionPointName);

  @NotNull <T> ExtensionPoint<@NotNull T> getExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName);
}
