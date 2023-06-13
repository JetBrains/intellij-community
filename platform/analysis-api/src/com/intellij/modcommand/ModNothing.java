// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * A command that does nothing
 */
public record ModNothing() implements ModCommand {
  public static final ModNothing NOTHING = new ModNothing();
  
  @Override
  public @NotNull void execute(@NotNull Project project) {
  }

  @Override
  public boolean isEmpty() {
    return true;
  }
}
