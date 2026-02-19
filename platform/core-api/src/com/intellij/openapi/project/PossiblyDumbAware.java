// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import org.jetbrains.annotations.Contract;

/**
 * Allows marking a distinct object as dumb-aware.
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
