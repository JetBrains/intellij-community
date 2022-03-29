package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.ui.popup.CodeVisionPopup
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.ViewableMap


class ProjectCodeVisionModel private constructor(val project: Project) {

  companion object {
    fun getInstance(project: Project): ProjectCodeVisionModel = project.service()

    const val MORE_PROVIDER_ID = "!More"
    const val HIDE_PROVIDER_ID = "!Hide"
    const val HIDE_ALL = "!HideAll"
  }

  val maxVisibleLensCount = ViewableMap<CodeVisionAnchorKind, Int>()
  val hoveredInlay = Property<Inlay<*>?>(null)
  val hoveredEntry = Property<CodeVisionEntry?>(null)
  val lensPopupActive = Property(false)

  val moreEntry = AdditionalCodeVisionEntry(MORE_PROVIDER_ID, "More...")


  private fun getCodeVisionHost() = CodeVisionHost.getInstance(project)

  fun handleLensClick(editor: Editor, range: TextRange, entry: CodeVisionEntry) {
    if (entry.providerId == MORE_PROVIDER_ID) {
      showMore()
      return
    }

    getCodeVisionHost().handleLensClick(editor, range, entry)
  }

  fun handleLensRightClick() {
    showContextPopup()
  }

  fun handleLensExtraAction(editor: Editor, range: TextRange, entry: CodeVisionEntry, actionId: String) {
    if (actionId == HIDE_PROVIDER_ID) {
      val id = CodeVisionHost.getInstance(project).getProviderById(entry.providerId)?.groupId ?: entry.providerId
      CodeVisionSettings.instance().setProviderEnabled(id, false)
      CodeVisionHost.getInstance(project).invalidateProviderSignal.fire(CodeVisionHost.LensInvalidateSignal(null))
      return
    }

    if (actionId == HIDE_ALL) {
      CodeVisionSettings.instance().codeVisionEnabled = false
      CodeVisionHost.getInstance(project).invalidateProviderSignal.fire(CodeVisionHost.LensInvalidateSignal(null))
      return
    }

    getCodeVisionHost().handleLensExtraAction(editor, range, entry, actionId)

  }

  fun getLensIndex(lens: CodeVisionEntry): Int {
    return getCodeVisionHost().getNumber(lens.providerId) + 1
  }

  private fun showMore() {
    val inlay = hoveredInlay.value ?: return
    val model = inlay.getUserData(CodeVisionListData.KEY) ?: return

    CodeVisionPopup.showMorePopup(
      model.lifetime,
      inlay,
      moreEntry,
      CodeVisionPopup.Disposition.MOUSE_POPUP_DISPOSITION,
      model.rangeCodeVisionModel,
      project
    )
  }

  private fun showContextPopup() {
    val inlay = hoveredInlay.value ?: return
    val entry = hoveredEntry.value ?: return

    if (entry.providerId == MORE_PROVIDER_ID) {
      showMore()
      return
    }

    val model = inlay.getUserData(CodeVisionListData.KEY) ?: return

    CodeVisionPopup.showContextPopup(
      model.lifetime,
      inlay,
      entry,
      CodeVisionPopup.Disposition.MOUSE_POPUP_DISPOSITION,
      model,
      project
    )
  }
}