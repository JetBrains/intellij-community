// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Symbol is an element in some model, e.g., language model or framework model.
 * <p/>
 * <h4>Lifecycle</h4>
 * The Symbol instance is expected to stay valid within a single read action,
 * which means it's safe to pass the instance to different APIs.<br/>
 * Symbol instance should not be referenced between read actions.
 * Please use {@link #createPointer() Pointer}'s {@link Pointer#dereference dereference}
 * to obtain a new Symbol instance (or the same instance if it's still valid, this is up to the Pointer)
 * in the subsequent read action.
 *
 * <h4>Equality contract</h4>
 * Implementations must define {@link #equals(Object)} and {@link #hashCode()} by semantic symbol identity,
 * not by object identity.
 * Equal symbols must represent the same symbol in the same model scope and be interchangeable
 * for platform caching and deduplication.
 *
 * @see com.intellij.model
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/symbols.html">Symbols (IntelliJ Platform Docs)</a>
 */
@ApiStatus.Experimental
public interface Symbol {

  /**
   * @return a pointer that can restore an equivalent Symbol in a subsequent read action
   */
  @NotNull Pointer<? extends Symbol> createPointer();

  /**
   * Required for using the instance in platform-level caches as a key.
   * <p/>
   * The platform will also check equality when several concurrent computations
   * return different instances, but only one instance should be cached.
   */
  @Override
  boolean equals(Object obj);

  @Override
  int hashCode();
}
