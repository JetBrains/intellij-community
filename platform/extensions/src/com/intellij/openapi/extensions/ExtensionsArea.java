// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import org.jetbrains.annotations.*;

import java.util.Map;

@ApiStatus.Internal
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

  <T> @Nullable ExtensionPoint<@NotNull T> getExtensionPointIfRegistered(@NotNull String extensionPointName);

  @NotNull <T> ExtensionPoint<@NotNull T> getExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName);

  @NotNull @Unmodifiable Map<String, ExtensionPointImpl<?>> getNameToPointMap();
}
