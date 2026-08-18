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
   * Returns the snapshot obtained by rebuilding every sputnik against [before]/[op]. [nextSnapshot] is invoked
   * after each sputnik that actually changes, so later sputniks in the same call can observe earlier ones'
   * rebuilt state via `after` in [DocumentSputnik.applyOp].
   *
   * Returns [after] itself if no sputnik changes.
   */
  @Contract(pure = true)
  fun applyOp(
    before: DocumentSnapshot,
    after: DocumentSnapshot,
    op: DocumentOp,
    nextSnapshot: (DocumentSputniks) -> DocumentSnapshot,
  ): DocumentSnapshot
}
