// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Immutable state attached to [DocumentText] and rebuilt on every text change to stay consistent with the text.
 *
 * Aspects are identified by [com.intellij.openapi.util.Key] identity.
 * Keep keys in static fields: a garbage-collected key leaves its aspect unreachable and unremovable.
 *
 * The snapshot-side attach/read API is not wired yet, so nothing creates or rebuilds aspects so far.
 */
@ApiStatus.Internal
interface DocumentAspect {

  /**
   * Returns the state of this aspect consistent with the text produced by applying [patch] to [beforeText].
   *
   * The method runs on the writer thread inside the document mutation producing the next [DocumentText],
   * so implementations must:
   * - be fast: this call is on the critical path of every document change;
   * - not throw: an exception aborts the text change halfway through;
   * - compute the new state only from [beforeText] (the snapshot before the change) and [patch]:
   *   the snapshot holding the result does not exist yet;
   * - return an aspect of the same type as `this`: the result stays associated with the same key.
   *
   * [DocumentTextPatch.originStartOffset]/[DocumentTextPatch.originEndOffset] expose the requested range before
   * prefix/suffix trimming, so an implementation can distinguish a narrowed change from an untrimmed one.
   */
  @Contract(pure = true)
  fun withText(
    beforeText: DocumentText,
    patch: DocumentTextPatch,
  ): DocumentAspect
}
