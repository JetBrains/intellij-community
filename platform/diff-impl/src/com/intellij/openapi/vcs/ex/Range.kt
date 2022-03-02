/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ex

internal typealias LstRange = com.intellij.openapi.vcs.ex.Range
internal typealias LstInnerRange = com.intellij.openapi.vcs.ex.Range.InnerRange

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
