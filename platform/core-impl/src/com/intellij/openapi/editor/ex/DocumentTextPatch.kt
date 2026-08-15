// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.impl.ComplexTextPatch
import com.intellij.openapi.editor.impl.SimpleTextPatch
import org.jetbrains.annotations.ApiStatus

/**
 * Snapshot-update instruction of a text change: the argument of [DocumentText.withPatch].
 *
 * [startOffset], [endOffset] and [newFragment] describe the applied replacement.
 * For a whole-text replacement the patch keeps the full range and the caller's untrimmed sequence
 * as [newFragment], so the snapshot stores that instance as is.
 *
 * [originStartOffset] and [originEndOffset] describe the requested replacement range before the common
 * prefix/suffix trimming, an analog of `DocumentEventImpl.initialStartOffset`/`initialOldLength`:
 * `startOffset != originStartOffset || endOffset != originEndOffset` means the change range was narrowed.
 *
 * [moveOffset] is the offset the changed fragment sticks to when the change is one half of a text move
 * ([DocumentEx.moveText]), an analog of `DocumentEventImpl.moveOffset`; it equals [startOffset] for
 * ordinary changes.
 */
@ApiStatus.Internal
interface DocumentTextPatch { // TODO: implement DocumentEventImpl via DocumentTextPatch
  fun startOffset(): Int
  fun endOffset(): Int
  fun newFragment(): CharSequence
  fun newModStamp(): Long
  fun clearLineFlags(): Boolean // TODO: remove
  fun originStartOffset(): Int
  fun originEndOffset(): Int
  fun moveOffset(): Int

  companion object {

    @JvmStatic
    fun simple(
      startOffset: Int,
      endOffset: Int,
      newFragment: CharSequence,
      newModStamp: Long,
      clearLineFlags: Boolean,
    ): DocumentTextPatch {
      return SimpleTextPatch(
        startOffset,
        endOffset,
        newFragment,
        newModStamp,
        clearLineFlags,
      )
    }

    @JvmStatic
    fun complex(
      startOffset: Int,
      endOffset: Int,
      newFragment: CharSequence,
      newModStamp: Long,
      clearLineFlags: Boolean,
      originStartOffset: Int,
      originEndOffset: Int,
      moveOffset: Int = startOffset,
    ): DocumentTextPatch {
      if (originStartOffset == startOffset &&
          originEndOffset == endOffset &&
          moveOffset == startOffset) {
        return simple(
          startOffset,
          endOffset,
          newFragment,
          newModStamp,
          clearLineFlags,
        )
      }
      return ComplexTextPatch(
        startOffset,
        endOffset,
        newFragment,
        newModStamp,
        clearLineFlags,
        originStartOffset,
        originEndOffset,
        moveOffset,
      )
    }
  }
}
