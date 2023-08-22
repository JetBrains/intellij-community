// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.LineNumberConstants
import java.util.*

abstract class LineStatusTrackerBlockOperations<R : Range, B : BlockI>(private val LOCK: DocumentTracker.Lock) {
  protected abstract fun getBlocks(): List<B>?
  protected abstract fun B.toRange(): R

  fun getRanges(): List<R>? {
    LOCK.read {
      val blocks = getBlocks() ?: return null
      return blocks.filter { !it.isEmpty }.map { it.toRange() }
    }
  }

  fun findRange(range: Range): R? = findBlock(range)?.toRange()

  fun findBlock(range: Range): B? {
    LOCK.read {
      val blocks = getBlocks() ?: return null
      for (block in blocks) {
        if (block.start == range.line1 &&
            block.end == range.line2 &&
            block.vcsStart == range.vcsLine1 &&
            block.vcsEnd == range.vcsLine2) {
          return block
        }
      }
      return null
    }
  }

  fun getNextRange(line: Int): R? {
    LOCK.read {
      val blocks = getBlocks() ?: return null
      for (block in blocks) {
        if (line < block.end && !block.isSelectedByLine(line)) {
          return block.toRange()
        }
      }
      return null
    }
  }

  fun getPrevRange(line: Int): R? {
    LOCK.read {
      val blocks = getBlocks() ?: return null
      for (block in blocks.reversed()) {
        if (line > block.start && !block.isSelectedByLine(line)) {
          return block.toRange()
        }
      }
      return null
    }
  }

  fun getRangesForLines(lines: BitSet): List<R>? {
    LOCK.read {
      val blocks = getBlocks() ?: return null
      val result = ArrayList<R>()
      for (block in blocks) {
        if (block.isSelectedByLine(lines)) {
          result.add(block.toRange())
        }
      }
      return result
    }
  }

  fun getRangeForLine(line: Int): R? {
    LOCK.read {
      val blocks = getBlocks() ?: return null
      for (block in blocks) {
        if (block.isSelectedByLine(line)) {
          return block.toRange()
        }
      }
      return null
    }
  }

  fun isLineModified(line: Int): Boolean {
    return isRangeModified(line, line + 1)
  }

  fun isRangeModified(startLine: Int, endLine: Int): Boolean {
    if (startLine == endLine) return false
    assert(startLine < endLine)

    LOCK.read {
      val blocks = getBlocks() ?: return false
      for (block in blocks) {
        if (block.start >= endLine) return false
        if (block.end > startLine) return true
      }
      return false
    }
  }

  fun transferLineFromVcs(line: Int, approximate: Boolean): Int = transferLine(line, approximate, true)
  fun transferLineToVcs(line: Int, approximate: Boolean): Int = transferLine(line, approximate, false)
  private fun transferLine(line: Int, approximate: Boolean, fromVcs: Boolean): Int {
    LOCK.read {
      val blocks = getBlocks()
      if (blocks == null) return if (approximate) line else LineNumberConstants.ABSENT_LINE_NUMBER

      var result = line

      for (block in blocks) {
        val startLine1 = if (fromVcs) block.vcsStart else block.start
        val endLine1 = if (fromVcs) block.vcsEnd else block.end
        val startLine2 = if (fromVcs) block.start else block.vcsStart
        val endLine2 = if (fromVcs) block.end else block.vcsEnd

        if (line in startLine1 until endLine1) {
          return if (approximate) startLine2 else LineNumberConstants.ABSENT_LINE_NUMBER
        }

        if (endLine1 > line) return result

        val length1 = endLine1 - startLine1
        val length2 = endLine2 - startLine2
        result += length2 - length1
      }
      return result
    }
  }

  companion object {
    @JvmStatic
    fun BlockI.isSelectedByLine(line: Int): Boolean = DiffUtil.isSelectedByLine(line, start, end)

    @JvmStatic
    fun BlockI.isSelectedByLine(lines: BitSet): Boolean = DiffUtil.isSelectedByLine(lines, start, end)
  }
}

interface BlockI {
  val start: Int
  val end: Int
  val vcsStart: Int
  val vcsEnd: Int

  val isEmpty: Boolean get() = start == end && vcsStart == vcsEnd
}