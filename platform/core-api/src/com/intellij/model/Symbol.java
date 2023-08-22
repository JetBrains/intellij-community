// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import org.jetbrains.annotations.NotNull;

/**
 * Symbol is an element in some model, e.g. language model or framework model.
 * <p/>
 * <h4>Lifecycle</h4>
 * The Symbol instance is expected to stay valid within a single read action,
 * which means it's safe to pass the instance to different APIs.<br/>
 * Symbol instance should not be referenced between read actions.
 * Please use {@link #createPointer() Pointer}'s {@link Pointer#dereference dereference}
 * to obtain a new Symbol instance (or the same instance if it's still valid, this is up to the Pointer)
 * in the subsequent read action.
 *
 * @see com.intellij.model
 */
public interface Symbol {

  /**
   * @return a pointer which is used to restore the Symbol between read actions
   */
  @NotNull Pointer<? extends Symbol> createPointer();

  /**
   * Required for using the instance in platform-level caches as a key.
   * <p/>
   * The platform will also check the equality when several concurrent computations
   * return different instances but only one instance should be cached.
   */
  boolean equals(Object obj);

  int hashCode();
}
