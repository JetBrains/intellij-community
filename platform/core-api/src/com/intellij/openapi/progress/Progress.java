// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.CancellationException;

@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface Progress {

  /**
   * @return whether the computation has been canceled. Usually {@link #checkCancelled()} is called instead
   */
  boolean isCancelled();

  /**
   * Checks if the current computation has been canceled, and, if yes, stop it immediately (by throwing an exception).
   * Computations should call this frequently to allow for prompt cancellation. Failure to do this can cause UI freezes.
   *
   * @throws CancellationException if this progress has been canceled, i.e. {@link #isCancelled()} returns true.
   */
  default void checkCancelled() throws CancellationException {
    if (isCancelled()) {
      throw new CancellationException();
    }
  }
}
