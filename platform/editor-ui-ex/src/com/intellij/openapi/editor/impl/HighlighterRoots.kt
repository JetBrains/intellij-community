// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.PMarkerRootImpl
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import java.util.concurrent.atomic.AtomicReference

/**
 * Stores the persistent highlighter roots that belong to one document snapshot.
 *
 * [exactRoot] contains exact-range highlighters. [lineRoot] contains line-range highlighters.
 * The atomic references let the storage add or remove highlighters from an existing snapshot.
 *
 * [invalidatedMarkerIds] contains IDs for highlighters that became invalid while the snapshot was derived from its parent.
 */
internal class HighlighterRoots @JvmOverloads constructor(
  exactRoot: PMarkerRoot = PMarkerRootImpl.empty(),
  lineRoot: PMarkerRoot = PMarkerRootImpl.empty(),
  val invalidatedMarkerIds: LongArray = LongArray(0),
) {
  val exactRoot = AtomicReference(exactRoot)
  val lineRoot = AtomicReference(lineRoot)

  fun rootReference(targetArea: HighlighterTargetArea): AtomicReference<PMarkerRoot> {
    return if (targetArea == HighlighterTargetArea.EXACT_RANGE) exactRoot else lineRoot
  }
}
