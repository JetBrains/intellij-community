// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.ui;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Marker interface for {@link FacetEditorValidator} which indicates that
 * validator's {@code check()} method is slow, and it should be invoked on background thread.
 */
public interface SlowFacetEditorValidator {
  /**
   * Asynchronously checks the validation status.
   *
   * @return a future that represents the asynchronous validation result or <code>null</code>
   * if {@link FacetEditorValidator#check()} should be called in non-blocking read action instead.
   */
  default @Nullable CompletableFuture<ValidationResult> checkAsync() {
    return null;
  }
}
