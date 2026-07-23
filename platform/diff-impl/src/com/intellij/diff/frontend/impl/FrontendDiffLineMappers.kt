// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import com.intellij.diff.frontend.FrontendDiffLineLocation
import com.intellij.diff.frontend.FrontendDiffLineMapper
import com.intellij.diff.frontend.FrontendUnifiedDiffMapping
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Document
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FrontendDirectDiffLineMapper(
  private val document: Document,
  private val side: Side,
  private val otherSideLine: (Int) -> Int = { -1 },
) : FrontendDiffLineMapper {
  override fun locationToLine(location: FrontendDiffLineLocation): Int? = location.line.takeIf { location.side == side }

  override fun lineToLocation(line: Int): FrontendDiffLineLocation? =
    FrontendDiffLineLocation(side, line).takeIf { line in 0 until document.lineCount }

  override fun lineToUnified(line: Int): Pair<Int, Int> {
    val mappedLine = otherSideLine(line)
    return side.select(line to mappedLine, mappedLine to line)
  }
}

@ApiStatus.Internal
class FrontendUnifiedDiffLineMapper(
  private val document: Document,
  private val mapping: FrontendUnifiedDiffMapping,
  private val locationProvider: (Int) -> FrontendDiffLineLocation?,
) : FrontendDiffLineMapper {
  override fun locationToLine(location: FrontendDiffLineLocation): Int? =
    mapping.sideLineToUnified(location.side, location.line)

  override fun lineToLocation(line: Int): FrontendDiffLineLocation? =
    locationProvider(line).takeIf { line in 0 until document.lineCount }

  override fun lineToUnified(line: Int): Pair<Int, Int> = mapping.unifiedLineToSideLines(line)
}
