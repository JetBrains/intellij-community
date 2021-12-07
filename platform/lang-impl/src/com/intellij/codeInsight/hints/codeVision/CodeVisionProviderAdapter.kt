// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange

/**
 * Adapter between code [CodeVisionProvider] and [DaemonBoundCodeVisionProvider].
 *
 * Computes nothing, just shows results from cache, the main work happens in [CodeVisionPass].
 */
class CodeVisionProviderAdapter(private val delegate: DaemonBoundCodeVisionProvider) : CodeVisionProvider<Unit> {
  override fun precomputeOnUiThread(editor: Editor) {
    // nothing
  }

  override fun computeForEditor(editor: Editor, uiData: Unit): List<Pair<TextRange, CodeVisionEntry>> {
    val project = editor.project ?: return emptyList()
    val cacheService = DaemonBoundCodeVisionCacheService.getInstance(project)
    val cached = cacheService.getVisionDataForEditor(editor, id) ?: return emptyList()
    val document = editor.document
    // ranges may be slightly outdated, so we have to unsure that they fit the document
    val lenses = cached.map {
      val range = it.first
      if (document.textLength <= range.endOffset) {
        TextRange(range.startOffset, document.textLength) to it.second
      }
      else {
        it
      }
    }
    return lenses
  }

  override fun handleClick(editor: Editor, textRange: TextRange) {
    delegate.handleClick(editor, textRange)
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