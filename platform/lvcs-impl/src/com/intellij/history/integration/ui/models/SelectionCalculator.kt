// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.ui.models

import com.intellij.diff.Block
import com.intellij.history.core.tree.Entry
import com.intellij.history.integration.IdeaGateway
import com.intellij.platform.lvcs.impl.RevisionId
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

abstract class SelectionCalculator(private val gateway: IdeaGateway,
                                   private val revisions: List<RevisionId>,
                                   private val fromLine: Int,
                                   private val toLine: Int) {
  private val cache: Int2ObjectMap<Block> = Int2ObjectOpenHashMap()

  fun canCalculateFor(revision: RevisionId, progress: Progress): Boolean {
    try {
      doGetSelectionFor(revision, progress)
    }
    catch (e: ContentIsUnavailableException) {
      return false
    }
    return true
  }

  fun getSelectionFor(revision: RevisionId, progress: Progress): Block {
    return doGetSelectionFor(revision, progress)
  }

  private fun doGetSelectionFor(revision: RevisionId, progress: Progress): Block {
    val target = revisions.indexOf(revision)
    return getSelectionFor(target, progress)
  }

  private fun getSelectionFor(revisionIndex: Int, progress: Progress): Block {
    val cached = cache[revisionIndex]
    if (cached != null) return cached

    val (lastNonEmptyIndex, lastNonEmptyBlock) = findLastNonEmptyBlock(revisionIndex)
    var lastBlock = lastNonEmptyBlock
    for (currentIndex in lastNonEmptyIndex + 1..revisionIndex) {
      val content = getRevisionContent(revisions[currentIndex])
      progress.processed((currentIndex + 1) * 100 / (revisionIndex + 1))

      val result = if (content == null) EMPTY_BLOCK
      else if (currentIndex == 0) Block(content, fromLine, toLine + 1)
      else {
        val nextBlock = lastBlock
        nextBlock.createPreviousBlock(content)
      }

      cache.put(currentIndex, result)
      if (result != EMPTY_BLOCK) lastBlock = result
    }

    return cache[revisionIndex] ?: EMPTY_BLOCK
  }

  private fun findLastNonEmptyBlock(revisionIndex: Int): IndexedValue<Block> {
    for (index in revisionIndex downTo 0) {
      val cachedBlock = cache[index]
      if (cachedBlock != null && cachedBlock != EMPTY_BLOCK) return IndexedValue(index, cachedBlock)
    }
    return IndexedValue(-1, EMPTY_BLOCK)
  }

  private fun getRevisionContent(revision: RevisionId): String? {
    val entry = getEntry(revision) ?: return null
    val content = entry.content
    if (!content.isAvailable) throw ContentIsUnavailableException()
    return content.getString(entry, gateway)
  }

  protected abstract fun getEntry(revision: RevisionId): Entry?

  private class ContentIsUnavailableException : RuntimeException()

  companion object {
    private val EMPTY_BLOCK = Block("", 0, 0)
  }
}
