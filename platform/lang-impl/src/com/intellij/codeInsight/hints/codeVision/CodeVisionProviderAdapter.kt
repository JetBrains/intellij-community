// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiModificationTracker

/**
 * Adapter between code [CodeVisionProvider] and [DaemonBoundCodeVisionProvider].
 *
 * Computes nothing, just shows results from cache, the main work happens in [CodeVisionPass].
 */
class CodeVisionProviderAdapter(private val delegate: DaemonBoundCodeVisionProvider) : CodeVisionProvider<Unit> {
  override fun precomputeOnUiThread(editor: Editor) {
    // nothing
  }

  override fun collectPlaceholders(editor: Editor): List<TextRange> {
    return delegate.collectPlaceholders(editor)
  }

  override fun shouldRecomputeForEditor(editor: Editor, uiData: Unit): Boolean {
    val project = editor.project ?: return super.shouldRecomputeForEditor(editor, uiData)
    val cacheService = DaemonBoundCodeVisionCacheService.getInstance(project)
    val modificationTracker = PsiModificationTracker.SERVICE.getInstance(editor.project)
    val cached = cacheService.getVisionDataForEditor(editor, id) ?: return super.shouldRecomputeForEditor(editor, uiData)

    return modificationTracker.modificationCount == cached.modificationStamp

  }

  override fun computeForEditor(editor: Editor, uiData: Unit): List<Pair<TextRange, CodeVisionEntry>> {
    val project = editor.project ?: return emptyList()
    val cacheService = DaemonBoundCodeVisionCacheService.getInstance(project)
    val cached = cacheService.getVisionDataForEditor(editor, id) ?: return emptyList()
    val document = editor.document
    // ranges may be slightly outdated, so we have to unsure that they fit the document
    val lenses = cached.codeVisionEntries.map {
      val range = it.first
      it.second.showInMorePopup = false
      if (document.textLength <= range.endOffset) {
        TextRange(range.startOffset, document.textLength) to it.second
      }
      else {
        it
      }
    }
    return lenses
  }

  override fun handleClick(editor: Editor, textRange: TextRange, entry: CodeVisionEntry) {
    delegate.handleClick(editor, textRange, entry)
  }

  override val name: String
    get() = delegate.name
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = delegate.relativeOrderings
  override val defaultAnchor: CodeVisionAnchorKind
    get() = delegate.defaultAnchor
  override val id: String
    get() = delegate.id
  override val groupId: String
    get() = delegate.groupId
}