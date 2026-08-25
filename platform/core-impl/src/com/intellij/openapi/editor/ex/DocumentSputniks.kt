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
   * Returns the snapshot obtained by applying [op] to the sputnik collection. For a text-changing
   * [DocumentTextPatch], [nextSnapshot] is invoked
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
