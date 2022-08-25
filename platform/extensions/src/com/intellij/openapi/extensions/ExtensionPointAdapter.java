// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;

/**
 * @see ExtensionPointChangeListener
 */
public abstract class ExtensionPointAdapter<T> implements ExtensionPointListener<T> {
  @Override
  public final void extensionAdded(@NotNull T extension, @NotNull PluginDescriptor pluginDescriptor) {
    extensionListChanged();
  }

  @Override
  public final void extensionRemoved(@NotNull T extension, @NotNull PluginDescriptor pluginDescriptor) {
    extensionListChanged();
  }

  /**
   * Fired when extensions are added or removed in the EP.
   */
  public abstract void extensionListChanged();
}
