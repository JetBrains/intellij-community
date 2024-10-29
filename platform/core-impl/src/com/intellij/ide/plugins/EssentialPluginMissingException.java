// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public class EssentialPluginMissingException extends RuntimeException {
  public final @NotNull List<String> pluginIds;

  EssentialPluginMissingException(@NotNull List<String> pluginIds) {
    super("Missing essential plugins: " + String.join(", ", pluginIds));
    this.pluginIds = pluginIds;
  }
}
