package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionBundle
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.jetbrains.rd.util.reactive.IProperty
import org.jetbrains.annotations.Nls

class RangeCodeVisionModel(
  val project: Project,
  val editor: Editor,
  lensMap: Map<CodeVisionAnchorKind, List<CodeVisionEntry>>,
  val anchoringRange: TextRange,
  @Nls val name: String = CodeVisionBundle.message("codeLens.more.popup.header")
) {

  enum class InlayState {
    NORMAL,
    ACTIVE
  }

  private val projectModel = ProjectCodeVisionModel.getInstance(project)
  private val lensForRange: List<CodeVisionEntry>
  val inlays: ArrayList<Inlay<*>> = ArrayList<Inlay<*>>()

  init {
    lensForRange = lensMap.flatMap { it.value }
  }

  fun lensPopupActive(): IProperty<Boolean> = projectModel.lensPopupActive
  fun handleLensClick(entry: CodeVisionEntry) {
    projectModel.handleLensClick(editor, anchoringRange, entry)
  }

  fun handleLensRightClick() {
    projectModel.handleLensRightClick()
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