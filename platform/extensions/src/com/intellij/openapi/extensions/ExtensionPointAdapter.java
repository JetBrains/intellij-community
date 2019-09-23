// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class ExtensionPointAdapter<T> implements ExtensionPointAndAreaListener<T> {
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
