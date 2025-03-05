// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

import andel.text.Text
import andel.text.TextRange


private fun requireRange(from: Long, to: Long) {
  require(from <= to) { "bad range ($from, $to)"}
}

fun Text.substring(textRange: TextRange): String {
  return view().string(textRange.start.toInt(), textRange.end.toInt())
}