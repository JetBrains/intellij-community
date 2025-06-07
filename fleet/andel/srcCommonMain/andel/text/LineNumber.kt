// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

import kotlin.jvm.JvmInline

@JvmInline
value class LineNumber(val line: Int) {
  operator fun compareTo(other: LineNumber): Int =
    line.compareTo(other.line)
  operator fun plus(other: LineNumber): LineNumber =
    LineNumber(line + other.line)
  operator fun minus(other: LineNumber): LineNumber =
    LineNumber(line - other.line)
}

val Int.line: LineNumber get() = LineNumber(this)

val Long.line: LineNumber
  get() = LineNumber(this.toInt())