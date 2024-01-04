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
    return getSelectionFor(target, target + 1, progress)
  }

  private fun getSelectionFor(revisionIndex: Int, totalRevisions: Int, progress: Progress): Block {
    val cached = cache[revisionIndex]
    if (cached != null) return cached

    val content = getRevisionContent(revisions[revisionIndex])
    progress.processed(((totalRevisions - revisionIndex) * 100) / totalRevisions)

    val result = if (content == null) EMPTY_BLOCK
    else if (revisionIndex == 0) Block(content, fromLine, toLine + 1)
    else {
      var nextBlock = EMPTY_BLOCK
      var i = revisionIndex
      while (nextBlock === EMPTY_BLOCK && i > 0) {
        i--
        nextBlock = getSelectionFor(i, totalRevisions, progress)
      }
      nextBlock.createPreviousBlock(content)
    }

    cache.put(revisionIndex, result)

    return result
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
