// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Thrown when several plugins conflict by registering the same functionality (for example, the same extension point).
 * It allows error handling to offer the user a choice of which plugin to disable.
 */
@ApiStatus.Internal
public final class PluginConflictException extends PluginException {
  private final @NotNull Set<PluginId> conflictingPluginIds;

  public PluginConflictException(@NotNull String message,
                                 @Nullable PluginId pluginId,
                                 @NotNull Collection<@NotNull PluginId> conflictingPluginIds) {
    super(message, pluginId);
    this.conflictingPluginIds = Collections.unmodifiableSet(new LinkedHashSet<>(conflictingPluginIds));
  }

  public @NotNull Set<@NotNull PluginId> getConflictingPluginIds() {
    return conflictingPluginIds;
  }
}
