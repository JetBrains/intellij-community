// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.timeline

import com.intellij.diff.tools.fragmented.LineNumberConvertor
import com.intellij.diff.util.LineRange
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import org.jetbrains.annotations.ApiStatus
import java.util.function.IntUnaryOperator

/**
 * The text of a patch hunk for one editor, with its changed blocks and the line numbers of both sides.
 *
 * In each changed block, the deleted lines go before the inserted lines.
 */
@ApiStatus.Internal
class PatchHunkLayout private constructor(
  val text: String,
  val blocks: List<Block>,
  /**
   * Maps a line of [text] to its line before the change, or to -1 when the line is new.
   */
  val lineConvertorBefore: IntUnaryOperator,
  /**
   * Maps a line of [text] to its line after the change, or to -1 when the line is deleted.
   */
  val lineConvertorAfter: IntUnaryOperator,
) {
  /**
   * The deleted lines and the inserted lines of one changed block, as line ranges of [text].
   */
  data class Block(val deletion: LineRange, val insertion: LineRange)

  companion object {
    fun of(hunk: PatchHunk): PatchHunkLayout {
      val text = StringBuilder()
      val convertorBefore = LineNumberConvertor.Builder()
      val convertorAfter = LineNumberConvertor.Builder()
      val blocks = mutableListOf<Block>()

      var totalLines = 0
      var lineBefore = hunk.startLineBefore
      var lineAfter = hunk.startLineAfter

      fun append(lines: List<PatchLine>) {
        for (line in lines) {
          text.append(line.text).append('\n')
        }
        totalLines += lines.size
      }

      forEachBlock(hunk.lines) { context, deletions, insertions ->
        convertorBefore.put(totalLines, lineBefore, context.size)
        convertorAfter.put(totalLines, lineAfter, context.size)
        append(context)
        lineBefore += context.size
        lineAfter += context.size

        val deletionStart = totalLines
        convertorBefore.put(totalLines, lineBefore, deletions.size)
        append(deletions)
        lineBefore += deletions.size

        val insertionStart = totalLines
        convertorAfter.put(totalLines, lineAfter, insertions.size)
        append(insertions)
        lineAfter += insertions.size

        val deletion = LineRange(deletionStart, insertionStart)
        val insertion = LineRange(insertionStart, totalLines)
        if (!deletion.isEmpty || !insertion.isEmpty) {
          blocks.add(Block(deletion, insertion))
        }
      }

      return PatchHunkLayout(text.toString(), blocks,
                             convertorBefore.build().createConvertor(),
                             convertorAfter.build().createConvertor())
    }

    /**
     * Splits [lines] into blocks of context lines, then deleted lines, then inserted lines.
     * A block ends where the context starts again, so one block keeps all the changed lines between two context lines.
     */
    private fun forEachBlock(
      lines: List<PatchLine>,
      consumer: (context: List<PatchLine>, deletions: List<PatchLine>, insertions: List<PatchLine>) -> Unit,
    ) {
      var lastType = PatchLine.Type.CONTEXT

      val context = mutableListOf<PatchLine>()
      val deletions = mutableListOf<PatchLine>()
      val insertions = mutableListOf<PatchLine>()

      for (line in lines) {
        val type = line.type
        if (lastType != PatchLine.Type.CONTEXT && type == PatchLine.Type.CONTEXT) {
          consumer(context, deletions, insertions)
          context.clear()
          deletions.clear()
          insertions.clear()
        }
        lastType = type

        when (type) {
          PatchLine.Type.CONTEXT -> context.add(line)
          PatchLine.Type.REMOVE -> deletions.add(line)
          PatchLine.Type.ADD -> insertions.add(line)
        }
      }
      consumer(context, deletions, insertions)
    }
  }
}
