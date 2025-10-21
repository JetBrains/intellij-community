// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionMessageBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.Nls

class RangeCodeVisionModel(
  val project: Project,
  val editor: Editor,
  lensMap: Map<CodeVisionAnchorKind, List<CodeVisionEntry>>,
  val anchoringRange: TextRange,
  @Nls val name: String = CodeVisionMessageBundle.message("codeLens.more.popup.header")
) {
  enum class InlayState {
    NORMAL,
    ACTIVE
  }

  private val projectModel = ProjectCodeVisionModel.getInstance(project)
  private val lensForRange: List<CodeVisionEntry> = lensMap.flatMap { it.value }
  val inlays: ArrayList<Inlay<*>> = ArrayList()

  fun handleLensClick(entry: CodeVisionEntry, anchorInlay: Inlay<*>) {
    projectModel.handleLensClick(editor, anchoringRange, anchorInlay, entry)
  }

  fun handleLensRightClick(clickedEntry: CodeVisionEntry, anchorInlay: Inlay<*>) {
    projectModel.handleLensRightClick(clickedEntry, anchorInlay)
  }

  fun handleLensExtraAction(selectedValue: CodeVisionEntry, actionId: String) {
    projectModel.handleLensExtraAction(editor, anchoringRange, selectedValue, actionId)
  }

  private fun sortedLenses(): List<CodeVisionEntry> {
    return lensForRange.sortedBy { projectModel.getLensIndex(it) }
  }

  fun sortedLensesMorePopup(): List<CodeVisionEntry> {
    return sortedLenses().filter { it.showInMorePopup }
  }

  fun state(): InlayState = InlayState.NORMAL
}