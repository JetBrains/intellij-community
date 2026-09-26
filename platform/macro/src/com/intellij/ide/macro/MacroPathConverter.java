// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Converts paths and path lists produced by macros.
 * <p>
 * Converter must be provided via the {@link MacroManager#PATH_CONVERTER_KEY}
 * in the {@code DataContext} passed to the {@link MacroManager}'s {@code expand*()} methods.
 * @see PathMacro
 * @see PathListMacro
 * @see MacroManager
 */
@ApiStatus.Internal
public interface MacroPathConverter {

  /**
   * Converts a path in the text expanded from a macro implementing {@link PathMacro}.
   */
  @NotNull
  String convertPath(@NotNull String path);

  /**
   * Converts a list of paths in the text expanded from a macro implementing {@link PathListMacro}.
   */
  @NotNull
  String convertPathList(@NotNull String pathList);
}
