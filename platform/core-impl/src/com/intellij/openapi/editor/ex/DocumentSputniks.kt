// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.Contract

/**
 * Immutable collection of [DocumentSputnik]s of a [DocumentSnapshot], keyed by [Key] identity
 */
internal interface DocumentSputniks {

  @Contract(pure = true)
  fun get(key: Key<out DocumentSputnik>): DocumentSputnik?

  /**
   * Returns a list where [key] is associated with [sputnik], replacing the current association if any
   */
  @Contract(pure = true)
  fun add(key: Key<out DocumentSputnik>, sputnik: DocumentSputnik): DocumentSputniks

  /**
   * Returns a list without the sputnik associated with [key]
   */
  @Contract(pure = true)
  fun remove(key: Key<out DocumentSputnik>): DocumentSputniks

  /**
   * Returns the snapshot obtained by rebuilding every sputnik against [before]/[diff], threading the result
   * through [nextSnapshot] after each sputnik that actually changes -- so that later sputniks rebuilt in this
   * same call can observe earlier ones' rebuilt state via the snapshot passed as `after` to
   * [DocumentSputnik.withTextChange] (see there for what that visibility does and does not guarantee).
   *
   * Returns [after] itself if no sputnik changes.
   */
  @Contract(pure = true)
  fun withTextChange(
    before: DocumentSnapshot,
    after: DocumentSnapshot,
    diff: DocumentTextPatch,
    nextSnapshot: (DocumentSputniks) -> DocumentSnapshot,
  ): DocumentSnapshot
}
