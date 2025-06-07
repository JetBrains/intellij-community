// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.aether;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.NotNull;

/**
 * Common retry interface for aether dependency resolver.
 */
@FunctionalInterface
public interface Retry {
  /**
   * @param computable Supplies that does some possibly throwing work.
   * @param logger   Messages logger.
   * @param <R>      Supplier result type.
   * @return Result from supplier.
   * @throws Exception An error the job thrown if attempts limit exceeded.
   */
  <R> R retry(@NotNull ThrowableComputable<? extends R, ? extends Exception> computable, @NotNull Logger logger) throws Exception;
}