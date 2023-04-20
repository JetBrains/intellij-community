// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Opaque command that just executes a supplied runnable. Used to support legacy API,
 * should not be used in client code
 * @param runnable runnable to execute
 */
public record ModLegacyBridge(@NotNull Runnable runnable) implements ModCommand {
  @Override
  public @NotNull ModStatus execute(@NotNull Project project) {
    runnable.run();
    return ModStatus.SUCCESS;
  }
}
