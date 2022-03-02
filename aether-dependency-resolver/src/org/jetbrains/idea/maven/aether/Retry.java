// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.aether;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * Common retry interface for aether dependency resolver.
 */
@FunctionalInterface
public interface Retry {
  /**
   * @param supplier Supplies that does some possibly throwing work.
   * @param logger   Messages logger.
   * @param <R>      Supplier result type.
   * @return Result from supplier.
   * @throws Exception An error the job thrown if attempts limit exceeded.
   */
  <R> R retry(@NotNull ThrowingSupplier<? extends R> supplier, @NotNull Logger logger) throws Exception;
}