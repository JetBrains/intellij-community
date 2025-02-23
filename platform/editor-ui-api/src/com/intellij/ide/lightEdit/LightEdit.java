// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * A wrapping class around {@link LightEditService} methods.
 */
public final class LightEdit {
  private LightEdit() {
  }

  @Contract("null -> false")
  public static boolean owns(@Nullable Project project) {
    return project instanceof LightEditCompatible;
  }
}
