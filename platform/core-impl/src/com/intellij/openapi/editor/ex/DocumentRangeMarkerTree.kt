// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.TestOnly

/**
 * This class is responsible for storing and querying a document's range markers.
 *
 * @see com.intellij.openapi.editor.Document.createRangeMarker(int, int, boolean)
 * @see com.intellij.openapi.editor.ex.DocumentEx.removeRangeMarker(RangeMarkerEx)
 * @see com.intellij.openapi.editor.ex.DocumentEx.processRangeMarkers(Processor)
 */
@ApiStatus.Internal
interface DocumentRangeMarkerTree {
  fun createRangeMarker(
    hostDocument: DocumentEx,
    startOffset: Int,
    endOffset: Int,
    surviveOnExternalChange: Boolean,
  ): RangeMarkerEx

  fun registerRangeMarker(
    rangeMarker: RangeMarkerEx,
    startOffset: Int,
    endOffset: Int,
    greedyToLeft: Boolean,
    greedyToRight: Boolean,
    layer: Int,
  )

  fun processRangeMarkersOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    processor: Processor<in RangeMarker>,
  ): Boolean

  fun removeRangeMarker(rangeMarker: RangeMarkerEx): Boolean

  fun createGuardedBlock(
    hostDocument: DocumentEx,
    startOffset: Int,
    endOffset: Int,
  ): RangeMarkerEx

  fun removeGuardedBlock(block: RangeMarker)

  @Contract(pure = true)
  fun getGuardedBlocks(): List<RangeMarker>

  @Contract(pure = true)
  fun getOffsetGuard(offset: Int): RangeMarkerEx?

  @Contract(pure = true)
  fun getRangeGuard(start: Int, end: Int): RangeMarkerEx?

  fun restoreRangeMarkersFromFile(
    source: VirtualFile,
    target: DocumentEx,
    tabSize: Int,
  )

  @Contract(pure = true)
  @TestOnly
  fun getRangeMarkersSize(): Int

  @Contract(pure = true)
  @TestOnly
  fun getRangeMarkersNodeSize(): Int
}
