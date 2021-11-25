// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Converts paths and path lists produced by macros.
 */
@ApiStatus.Internal
public interface MacroPathConverter {
  @NotNull
  String convertPath(@NotNull String path);

  @NotNull
  String convertPathList(@NotNull String pathList);
}
