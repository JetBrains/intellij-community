// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text.impl

import andel.rope.Rope

typealias TextCursor = Rope.Cursor<String>

internal fun TextCursor.substring(e: Any?, from: Int, to: Int, f: (String, Int, Int) -> Unit): TextCursor {
  if (from == to) return this
  var z1 = this
  while (true) {
    val str = z1.element
    val leafStart = z1.location(TextMonoid.CharsCount)
    val i = maxOf(from - leafStart, 0)
    val j = minOf(str.length, (to - leafStart))
    f(str, i, j)
    if (leafStart + str.length < to) {
      z1 = requireNotNull(z1.next(e)) {
        "out of bounds"
      }
    }
    else {
      break
    }
  }
  return z1
}

internal fun TextCursor.insert(editor: Any?, offset: Int, s: String): TextCursor {
  if (s.isEmpty()) {
    return this
  }
  val leaf = this
  val relCharOffset = offset - leaf.location(TextMonoid.CharsCount)
  val data = leaf.element
  val newData = data.substring(0, relCharOffset) + s + data.substring(relCharOffset)
  return leaf.replace(editor, newData)
}

internal fun TextCursor.delete(editor: Any?, offset: Int, l: Int): TextCursor {
  var loc = this
  var l = l
  while (l > 0) {
    val s = loc.element
    val relOffset = offset - loc.location(TextMonoid.CharsCount)
    val chunkLength = s.length
    val end = minOf(chunkLength, relOffset + l)
    val deleted = end - relOffset
    l -= deleted
    loc = run {
      val news = s.substring(0, relOffset) + s.substring(end)
      val newLeaf = loc.replace(editor, news)
      if (end == chunkLength) {
        if (l == 0) {
          newLeaf
        }
        else {
          newLeaf.next(editor)!!
        }
      }
      else {
        newLeaf
      }
    }
  }
  return loc
}
