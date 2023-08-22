// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

internal typealias LstRange = com.intellij.openapi.vcs.ex.Range
internal typealias LstInnerRange = com.intellij.openapi.vcs.ex.Range.InnerRange

/**
 * Stores half-open intervals [start, end).
 */
open class Range(val line1: Int,
                 val line2: Int,
                 val vcsLine1: Int,
                 val vcsLine2: Int,
                 val innerRanges: List<InnerRange>?) {
  constructor(line1: Int, line2: Int, vcsLine1: Int, vcsLine2: Int) : this(line1, line2, vcsLine1, vcsLine2, null)
  constructor(range: Range) : this(range.line1, range.line2, range.vcsLine1, range.vcsLine2)

  class InnerRange(val line1: Int, val line2: Int, val type: Byte)

  fun hasLines(): Boolean = line1 != line2
  fun hasVcsLines(): Boolean = vcsLine1 != vcsLine2

  val type: Byte
    get() {
      if (!hasLines() && !hasVcsLines()) return MODIFIED
      if (!hasLines()) return DELETED
      if (!hasVcsLines()) return INSERTED
      return MODIFIED
    }

  override fun toString(): String = "[$vcsLine1, $vcsLine2) - [$line1, $line2)"

  companion object {
    const val EQUAL: Byte = 0 // difference only in whitespaces
    const val MODIFIED: Byte = 1
    const val INSERTED: Byte = 2
    const val DELETED: Byte = 3
  }
}
