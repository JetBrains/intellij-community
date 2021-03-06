// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;

/**
 * See {@link ExtensionPointChangeListener}
 */
public interface ExtensionPointListener<T> {
  default void extensionAdded(@NotNull T extension, @NotNull PluginDescriptor pluginDescriptor) {
  }

  default void extensionRemoved(@NotNull T extension, @NotNull PluginDescriptor pluginDescriptor) {
  }

  static <T> ExtensionPointListener<T> @NotNull [] emptyArray() {
    //noinspection unchecked
    return (ExtensionPointListener<T>[])EMPTY_ARRAY;
  }
  ExtensionPointListener<?>[] EMPTY_ARRAY = new ExtensionPointListener<?>[0];
}
