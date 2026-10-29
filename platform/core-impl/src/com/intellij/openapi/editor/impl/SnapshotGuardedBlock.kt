// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.impl.marker.VirtualFileMarkerRoot
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.PersistentMarkerPolicy
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.util.TextRangeScalarUtil

/** A guarded block whose offsets are stored in the snapshot marker engine. */
internal class SnapshotGuardedBlock private constructor(
    document: DocumentImpl,
    fileRoot: VirtualFileMarkerRoot?,
    initialSpec: Byte,
    initialRange: Long,
) : SnapshotRangeMarkerImpl(document, fileRoot, initialSpec, initialRange, retainStrong = true) {
  override fun getFlavorFlags(): Byte = GuardedBlock.GUARD_BLOCK_FLAVOR_FLAG

  companion object {
    @JvmStatic
    fun create(document: DocumentImpl, startOffset: Int, endOffset: Int): SnapshotGuardedBlock {
      val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
      val initialRange = TextRangeScalarUtil.toScalarRange(startOffset, endOffset)
      return SnapshotMarkerEngineImpl.createRangeMarker(
        document,
        startOffset,
        endOffset,
        spec,
        PersistentMarkerPolicy,
      ) { fileRoot ->
        SnapshotGuardedBlock(document, fileRoot, spec, initialRange)
      }
    }
  }
}
