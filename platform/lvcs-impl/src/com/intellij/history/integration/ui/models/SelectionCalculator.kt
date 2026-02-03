// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui.models

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.diff.Block
import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.tree.Entry
import com.intellij.history.core.tree.RootEntry
import com.intellij.history.integration.IdeaGateway
import com.intellij.platform.lvcs.impl.RevisionId
import com.intellij.platform.lvcs.impl.diff.findEntry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class SelectionCalculator(private val gateway: IdeaGateway,
                                   internal val revisions: List<RevisionId>,
                                   private val fromLine: Int,
                                   private val toLine: Int) {
  private val cache = ConcurrentCollectionFactory.createConcurrentIntObjectMap<Block>()

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

  fun processContents(processor: (Long, String) -> Boolean) {
    for ((index, revisionId) in revisions.withIndex()) {
      val block = getSelectionFor(index, Progress.EMPTY)
      if (revisionId is RevisionId.ChangeSet) {
        if (!processor(revisionId.id, block.blockContent)) break
      }
    }
  }

  private class ContentIsUnavailableException : RuntimeException()

  companion object {
    private val EMPTY_BLOCK = Block("", 0, 0)

    @JvmOverloads
    @JvmStatic
    fun create(facade: LocalHistoryFacade,
               gateway: IdeaGateway,
               rootEntry: RootEntry,
               entryPath: String,
               revisions: List<RevisionId>,
               fromLine: Int,
               toLine: Int,
               isOldContentUsed: Boolean = true): SelectionCalculator {
      return object : SelectionCalculator(gateway, revisions, fromLine, toLine) {
        override fun getEntry(revision: RevisionId): Entry? {
          return facade.findEntry(rootEntry, revision, entryPath, isOldContentUsed)
        }
      }
    }
  }
}
