// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public interface ExtensionsArea  {
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

  @NotNull
  <T> ExtensionPoint<T> getExtensionPoint(@NonNls @NotNull String extensionPointName);

  @Nullable
  <T> ExtensionPoint<T> getExtensionPointIfRegistered(@NotNull String extensionPointName);

  @NotNull
  <T> ExtensionPoint<T> getExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName);
}
