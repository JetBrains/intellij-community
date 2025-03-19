// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text.impl

import andel.operation.Op
import andel.operation.Operation
import andel.rope.Rope

internal typealias TextRope = Rope<String>

internal val TextRope.linesCount: Int
  get() = size(TextMonoid.NewlinesCount) + 1

internal val TextRope.charCount: Int
  get() = size(TextMonoid.CharsCount)

internal fun TextRope.edit(operation: Operation) = run {
  val editor = Any()
  operation.ops.fold(cursor(editor) to 0) { (z, offset), op ->
    when (op) {
      is Op.Replace ->
        (z
           .scan(editor, TextMonoid.CharsCount, offset)
           .delete(editor, offset, op.delete.length)
           .insert(editor, offset, op.insert)) to (offset + op.insert.length)
      is Op.Retain -> z to (offset + op.len.toInt())
    }
  }.first.rope(editor)
}
