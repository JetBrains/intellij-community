// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.impl.marker.FileMarkerRoot
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.PersistentMarkerPolicy
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.util.TextRange

/** A guarded block whose offsets are stored in the snapshot marker engine. */
internal class SnapshotGuardedBlock private constructor(
  document: DocumentImpl,
  fileRoot: FileMarkerRoot?,
  markerId: Long,
  initialSpec: MarkerSpec,
  initialRange: TextRange,
) : SnapshotRangeMarkerImpl(document, fileRoot, markerId, initialSpec, initialRange) {
  override fun getFlavorFlags(): Byte = GuardedBlock.GUARD_BLOCK_FLAVOR_FLAG

  companion object {
    @JvmStatic
    fun create(document: DocumentImpl, startOffset: Int, endOffset: Int): SnapshotGuardedBlock {
      val snapshot = document.core.snapshot()
      val spec = MarkerSpec(false, false, policy = PersistentMarkerPolicy)
      val initialRange = TextRange(startOffset, endOffset)
      return SnapshotMarkerEngineImpl.createRangeMarker(
        document,
        snapshot,
        startOffset,
        endOffset,
        spec,
        retainStrong = true,
      ) { fileRoot, markerId ->
        SnapshotGuardedBlock(document, fileRoot, markerId, spec, initialRange)
      }
    }
  }
}
