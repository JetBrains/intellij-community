// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Thrown when two plugins conflict by registering the same functionality (for example, the same extension point).
 * It allows error handling to offer the user a choice of which plugin to disable.
 */
@ApiStatus.Internal
public final class PluginConflictException extends PluginException {
  public final PluginId conflictingPluginId;

  public PluginConflictException(@NotNull String message, @NotNull PluginId pluginId, @NotNull PluginId conflictingPluginId) {
    super(message, pluginId);
    this.conflictingPluginId = conflictingPluginId;
  }
}
