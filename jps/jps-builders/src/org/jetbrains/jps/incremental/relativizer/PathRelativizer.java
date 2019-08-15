// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import org.jetbrains.annotations.NotNull;

/**
 * It allows to implement handler to serve certain file paths, converting them from
 * absolute to relative and vice versa.
 */
public interface PathRelativizer {

  /**
   * Returns {@code true} if specified path can be converted to the relative by this
   * relativizer
   */
  boolean isAcceptableAbsolutePath(@NotNull String path);

  /**
   * Returns {@code true} if specified path can be converted to the absolute by this
   * relativizer
   */
  boolean isAcceptableRelativePath(@NotNull String path);

  /**
   * Convert concrete path to the relative. It's recommended to use it in conjunction
   * with {@link #isAcceptableAbsolutePath(String)} to avoid redundant method invoke.
   */
  String toRelativePath(@NotNull String path);

  /**
   * Convert concrete path to the absolute. It's recommended to use it in conjunction
   * with {@link #isAcceptableRelativePath(String)} to avoid redundant method invoke.
   */
  String toAbsolutePath(@NotNull String path);
}
