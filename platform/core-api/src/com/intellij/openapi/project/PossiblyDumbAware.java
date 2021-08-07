// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import org.jetbrains.annotations.Contract;

/**
 * This interface allows to mark a distinct object as dumb-aware.
 *
 * @see DumbAware
 */
public interface PossiblyDumbAware {
  @Contract(pure = true)
  default boolean isDumbAware() {
    //noinspection SSBasedInspection
    return this instanceof DumbAware;
  }
}
