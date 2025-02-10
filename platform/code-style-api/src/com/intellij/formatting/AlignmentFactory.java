// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Internal interface for creating alignment object instances.
 */
@ApiStatus.Internal
public interface AlignmentFactory {

  /**
   * Provides implementation for {@link Alignment#createAlignment(boolean, Alignment.Anchor)}.
   * 
   * @param allowBackwardShift    flag that specifies if former aligned block may be shifted to right in order to align to subsequent
   *                              aligned block
   * @param anchor                alignment anchor
   * @return                      alignment object with the given settings
   */
  Alignment createAlignment(boolean allowBackwardShift, @NotNull Alignment.Anchor anchor);

  /**
   * Provides 
   *
   * @param base    base alignment to use within returned alignment object
   * @return        alignment object with the given alignment defined as a {@code 'base alignment'}
   */
  Alignment createChildAlignment(final Alignment base);
}
