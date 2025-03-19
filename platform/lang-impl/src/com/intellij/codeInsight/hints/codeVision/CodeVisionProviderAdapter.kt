// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.hints.settings.language.isInlaySettingsEditor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.annotations.ApiStatus
import kotlin.math.min

/**
 * Adapter between code [CodeVisionProvider] and [DaemonBoundCodeVisionProvider].
 *
 * Computes nothing, just shows results from cache, the main work happens in [CodeVisionPass].
 */
@ApiStatus.Internal
class CodeVisionProviderAdapter(internal val delegate: DaemonBoundCodeVisionProvider) : CodeVisionProvider<Unit> {
  override fun precomputeOnUiThread(editor: Editor) {
    // nothing
  }

  override fun preparePreview(editor: Editor, file: PsiFile) {
    delegate.preparePreview(editor, file)
  }

  override fun getPlaceholderCollector(editor: Editor, psiFile: PsiFile?): CodeVisionPlaceholderCollector? {
    return delegate.getPlaceholderCollector(editor, psiFile)
  }

  override fun shouldRecomputeForEditor(editor: Editor, uiData: Unit?): Boolean {
    if (isInlaySettingsEditor(editor)) return true
    val project = editor.project ?: return super.shouldRecomputeForEditor(editor, uiData)
    val cacheService = DaemonBoundCodeVisionCacheService.getInstance(project)
    return runReadAction {
      val modificationTracker = PsiModificationTracker.getInstance(editor.project)
      val cached = cacheService.getVisionDataForEditor(editor, id)
                   ?: return@runReadAction super.shouldRecomputeForEditor(editor, uiData)
      modificationTracker.modificationCount == cached.modificationStamp
    }
  }

  override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
    val project = editor.project ?: return CodeVisionState.NotReady
    val cacheService = DaemonBoundCodeVisionCacheService.getInstance(project)
    return runReadAction {
      val cached = cacheService.getVisionDataForEditor(editor, id)
                   ?: return@runReadAction CodeVisionState.NotReady
      val document = editor.document
      // ranges may be slightly outdated, so we have to unsure that they fit the document
      val lenses = cached.codeVisionEntries.map {
        val range = it.first
        it.second.showInMorePopup = false
        if (document.textLength <= range.endOffset) {
          TextRange(min(document.textLength, range.startOffset), document.textLength) to it.second
        }
        else {
          it
        }
      }
      CodeVisionState.Ready(lenses)
    }
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