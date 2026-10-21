// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

/** Packs marker boundary flags into a [Byte]. */
object MarkerSpec {
  private const val GREEDY_TO_LEFT: Int = 1
  private const val GREEDY_TO_RIGHT: Int = 2
  private const val STICKING_TO_RIGHT: Int = 4

  @JvmStatic
  @JvmOverloads
  fun create(isGreedyToLeft: Boolean, isGreedyToRight: Boolean, isStickingToRight: Boolean = false): Byte {
    var flags = 0
    if (isGreedyToLeft) flags = flags or GREEDY_TO_LEFT
    if (isGreedyToRight) flags = flags or GREEDY_TO_RIGHT
    if (isStickingToRight) flags = flags or STICKING_TO_RIGHT
    return flags.toByte()
  }

  /**
   * Controls insertion at the marker start offset.
   * When `true`, the range includes inserted text and the start stays before it.
   * When `false`, the start moves after the inserted text.
   */
  fun isGreedyToLeft(spec: Byte): Boolean = spec.toInt() and GREEDY_TO_LEFT != 0

  /**
   * Controls insertion at the marker end offset.
   * When `true`, the range includes inserted text and the end moves after it.
   * When `false`, the end stays before the inserted text.
   */
  fun isGreedyToRight(spec: Byte): Boolean = spec.toInt() and GREEDY_TO_RIGHT != 0

  /**
   * Controls which side of inserted text a zero-length marker stays on.
   * When `true`, a non-greedy marker moves after text inserted at its offset.
   * When `false`, it stays before the inserted text.
   */
  fun isStickingToRight(spec: Byte): Boolean = spec.toInt() and STICKING_TO_RIGHT != 0

  fun withGreedyToLeft(spec: Byte, value: Boolean): Byte = withFlag(spec, GREEDY_TO_LEFT, value)

  fun withGreedyToRight(spec: Byte, value: Boolean): Byte = withFlag(spec, GREEDY_TO_RIGHT, value)

  fun withStickyToRight(spec: Byte, value: Boolean): Byte = withFlag(spec, STICKING_TO_RIGHT, value)

  private fun withFlag(spec: Byte, flag: Int, value: Boolean): Byte =
    (if (value) spec.toInt() or flag else spec.toInt() and flag.inv()).toByte()
}
