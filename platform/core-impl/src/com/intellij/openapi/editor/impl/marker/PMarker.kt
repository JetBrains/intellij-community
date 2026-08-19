// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.RangeMarkerEx

/**
 * Stable marker handle that can be resolved against several immutable
 * snapshots.
 *
 * The inherited IntelliJ [com.intellij.openapi.editor.RangeMarker] methods are
 * not snapshot-aware:
 *
 * - `getStartOffset()`
 * - `getEndOffset()`
 * - `isValid()`
 * - `getTextRange()`
 *
 * An implementation must define which active snapshot those inherited methods
 * use. Code that needs an exact historical or branch-specific result should
 * use the overloads accepting [DocumentSnapshot].
 *
 * The implementation must also provide the other members inherited from
 * IntelliJ `RangeMarker`, including its document, greedy setters, disposal,
 * and user-data support.
 */
interface PMarker : RangeMarkerEx {
  /**
   * Resolves this marker against the root associated with the exact [snapshot] instance.
   */
  fun resolve(snapshot: DocumentSnapshot): PMarkerResolution

  /**
   * Returns the marker start offset in [snapshot].
   */
  fun getStartOffset(snapshot: DocumentSnapshot): Int = resolve(snapshot).startOffset

  /**
   * Returns the marker end offset in [snapshot].
   */
  fun getEndOffset(snapshot: DocumentSnapshot): Int = resolve(snapshot).endOffset
}
