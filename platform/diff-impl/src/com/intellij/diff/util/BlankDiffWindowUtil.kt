// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import com.intellij.diff.actions.RecentBlankContent
import com.intellij.diff.actions.createEditableContent
import com.intellij.diff.actions.impl.MutableDiffRequestChain
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.ide.CopyPasteManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.UIBundle
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.LinkedListWithSum
import kotlin.math.max

/**
 * Utility methods to show a mutable diff window that allows swapping compared files on-the-fly.
 *
 * See [com.intellij.diff.actions.ShowBlankDiffWindowAction]
 */
object BlankDiffWindowUtil {
  val BLANK_KEY = Key.create<Boolean>("Diff.BlankWindow")

  @JvmField
  val REMEMBER_CONTENT_KEY = Key.create<Boolean>("Diff.BlankWindow.BlankContent")

  @JvmStatic
  fun createBlankDiffRequestChain(project: Project?): MutableDiffRequestChain =
    createBlankDiffRequestChain(createEditableContent(project), createEditableContent(project), project = project)

  @JvmStatic
  @JvmOverloads
  fun createBlankDiffRequestChain(
    content1: DocumentContent,
    content2: DocumentContent,
    baseContent: DocumentContent? = null,
    project: Project? = null,
  ): MutableDiffRequestChain {
    val chain = MutableDiffRequestChain(content1, baseContent, content2, project)
    chain.putUserData(BLANK_KEY, true)
    return chain
  }

  @JvmStatic
  fun setupBlankContext(chain: DiffRequestChain) {
    chain.putUserData(DiffUserDataKeys.PLACE, DiffPlaces.BLANK)
    chain.putUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE, Side.LEFT)
    chain.putUserData(DiffUserDataKeysEx.DISABLE_CONTENTS_EQUALS_NOTIFICATION, true)
  }

  private val ourRecentFiles = LinkedListWithSum<RecentBlankContent> { it.text.length }

  internal fun getRecentFiles(): List<RecentBlankContent> = ourRecentFiles.toList()

  @RequiresEdt
  fun saveRecentContents(request: DiffRequest) {
    if (request is ContentDiffRequest) {
      for (content in request.contents) {
        saveRecentContent(content)
      }
    }
  }

  @RequiresEdt
  fun saveRecentContent(content: DiffContent) {
    if (content !is DocumentContent) return
    if (!DiffUtil.isUserDataFlagSet(REMEMBER_CONTENT_KEY, content)) return

    val text = content.document.text
    if (text.isBlank()) return

    val oldValue = ourRecentFiles.find { it.text == text }
    if (oldValue != null) {
      ourRecentFiles.remove(oldValue)
      ourRecentFiles.add(0, oldValue)
    }
    else {
      ourRecentFiles.add(0, RecentBlankContent(text, System.currentTimeMillis()))
      deleteAfterAllowedMaximum()
    }
  }

  private fun deleteAfterAllowedMaximum() {
    val maxCount = max(1, Registry.intValue("blank.diff.history.max.items"))
    val maxMemory = max(0, Registry.intValue("blank.diff.history.max.memory"))
    CopyPasteManagerEx.deleteAfterAllowedMaximum(ourRecentFiles, maxCount, maxMemory) { item ->
      RecentBlankContent(UIBundle.message("clipboard.history.purged.item"), item.timestamp)
    }
  }
}