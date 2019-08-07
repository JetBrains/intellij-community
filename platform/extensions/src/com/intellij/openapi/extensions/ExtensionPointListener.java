// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;

public interface ExtensionPointListener<T> {
  ExtensionPointListener[] EMPTY_ARRAY = new ExtensionPointListener[0];

  /**
   * Fired when an extension is explicitly registered in the EP. Note that when a plugin is dynamically loaded and contributes extensions
   * to this EP, the {@link #extensionListChanged()} method is called, in order to avoid instantiating the extensions.
   */
  default void extensionAdded(@NotNull T extension, @NotNull PluginDescriptor pluginDescriptor) {
   extensionListChanged();
  }

  default void extensionRemoved(@NotNull T extension, @NotNull PluginDescriptor pluginDescriptor) {
    extensionListChanged();
  }

  /**
   * Fired when extensions are added or removed in the EP.
   */
  default void extensionListChanged() {
  }
}
