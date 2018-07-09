// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ExtensionPointListener<T> {
  ExtensionPointListener[] EMPTY_ARRAY = new ExtensionPointListener[0];
  default void extensionAdded(@NotNull T extension, @Nullable PluginDescriptor pluginDescriptor) {
  }

  default void extensionRemoved(@NotNull T extension, @Nullable PluginDescriptor pluginDescriptor) {
  }
}
