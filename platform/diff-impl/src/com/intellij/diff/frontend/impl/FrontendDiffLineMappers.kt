// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import com.intellij.diff.frontend.FrontendDiffLineLocation
import com.intellij.diff.frontend.FrontendDiffLineMapper
import com.intellij.diff.frontend.FrontendUnifiedDiffMapping
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import org.jetbrains.annotations.ApiStatus

/**
 * Maps lines between the LEFT and RIGHT documents of a two-side diff viewer, as [FrontendDirectDiffLineMapper] needs them.
 *
 * In split mode the mapping is computed on the backend, so [isAvailable] reports whether it currently matches the frontend
 * documents, and [addListener] notifies when that changes.
 */
@ApiStatus.Internal
interface FrontendTwoSideDiffMapping {
  val isAvailable: Boolean
  val revision: Long

  fun addListener(parentDisposable: Disposable, listener: () -> Unit)

  /** Maps [line] of [side] onto the opposite side, or returns `-1` when there is no such line. */
  fun mapOtherSide(side: Side, line: Int): Int
}

/**
 * Maps the editor of a one-side or two-side viewer, where editor lines are the lines of [side] itself.
 *
 * [otherSideMapping] is what makes the opposite side of [lineToUnified] resolvable; without it, and while it is unavailable,
 * only [side] is reported.
 */
@ApiStatus.Internal
class FrontendDirectDiffLineMapper(
  private val document: Document,
  private val side: Side,
  private val otherSideMapping: FrontendTwoSideDiffMapping? = null,
) : FrontendDiffLineMapper {
  override val isAvailable: Boolean get() = otherSideMapping?.isAvailable ?: true

  override fun addListener(parentDisposable: Disposable, listener: () -> Unit) {
    otherSideMapping?.addListener(parentDisposable, listener)
  }

  override fun locationToLine(location: FrontendDiffLineLocation): Int? = location.line.takeIf { location.side == side }

  override fun lineToLocation(line: Int): FrontendDiffLineLocation? =
    FrontendDiffLineLocation(side, line).takeIf { line in 0 until document.lineCount }

  override fun lineToUnified(line: Int): Pair<Int, Int> {
    val mappedLine = otherSideMapping?.mapOtherSide(side, line) ?: -1
    return side.select(line to mappedLine, mappedLine to line)
  }
}

@ApiStatus.Internal
class FrontendUnifiedDiffLineMapper(
  private val document: Document,
  private val mapping: FrontendUnifiedDiffMapping,
  private val locationProvider: (Int) -> FrontendDiffLineLocation?,
) : FrontendDiffLineMapper {
  override val isAvailable: Boolean get() = mapping.isAvailable

  override fun addListener(parentDisposable: Disposable, listener: () -> Unit) {
    mapping.addListener(parentDisposable, listener)
  }

  override fun locationToLine(location: FrontendDiffLineLocation): Int? =
    mapping.sideLineToUnified(location.side, location.line)

  override fun lineToLocation(line: Int): FrontendDiffLineLocation? =
    locationProvider(line).takeIf { line in 0 until document.lineCount }

  override fun lineToUnified(line: Int): Pair<Int, Int> = mapping.unifiedLineToSideLines(line)
}
