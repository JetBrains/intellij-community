// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.util.TextRange

/**
 * Result of resolving a marker against one [PMarkerRoot].
 */
sealed class PMarkerResolution(startOffset: Int, endOffset: Int) : TextRange(startOffset, endOffset) {
  /**
   * Whether the marker is valid in the resolved root.
   */
  val isValid: Boolean
    get() = this is Valid

  /**
   * Whether the marker is absent from the root.
   */
  val isAbsent: Boolean
    get() = this is Absent

  /**
   * A marker with usable offsets.
   *
   * The represented range is half-open:
   *
   *     [startOffset, endOffset)
   */
  class Valid(startOffset: Int, endOffset: Int) : PMarkerResolution(startOffset, endOffset)

  /**
   * A marker that exists but no longer has valid offsets in the resolved snapshot.
   *
   * The inherited range contains the offsets immediately before invalidation.
   */
  class Invalid(
    val reason: String,
    startOffset: Int,
    endOffset: Int,
  ) : PMarkerResolution(startOffset, endOffset) {
    init {
      require(reason.isNotBlank()) {
        "reason must not be blank"
      }
    }
  }

  /**
   * A marker that is not part of the root. The inherited range contains its last known or creation-time offsets.
   */
  class Absent(startOffset: Int, endOffset: Int) : PMarkerResolution(startOffset, endOffset)
}
