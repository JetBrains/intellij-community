package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.ViewableMap

interface ProjectCodeVisionModel {
  companion object {
    fun getInstance(project: Project): ProjectCodeVisionModel = project.service()
    const val MORE_PROVIDER_ID: String = "!More"
    const val HIDE_PROVIDER_ID: String = "!Hide"
    const val HIDE_ALL: String = "!HideAll"
  }

  val maxVisibleLensCount: ViewableMap<CodeVisionAnchorKind, Int>
  val lensPopupActive: Property<Boolean>
  val moreEntry: AdditionalCodeVisionEntry
  fun handleLensClick(editor: Editor, range: TextRange, anchorInlay: Inlay<*>, entry: CodeVisionEntry)
  fun handleLensRightClick(clickedEntry: CodeVisionEntry, anchorInlay: Inlay<*>)
  fun handleLensExtraAction(editor: Editor, range: TextRange, entry: CodeVisionEntry, actionId: String)
  fun getLensIndex(lens: CodeVisionEntry): Int
}