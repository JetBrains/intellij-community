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

    const val MORE_PROVIDER_ID: String = "!More"
    const val HIDE_PROVIDER_ID: String = "!Hide"
    const val HIDE_ALL: String = "!HideAll"
  }

  val maxVisibleLensCount: ViewableMap<CodeVisionAnchorKind, Int> = ViewableMap<CodeVisionAnchorKind, Int>()
  val hoveredInlay: Property<Inlay<*>?> = Property<Inlay<*>?>(null)
  val hoveredEntry: Property<CodeVisionEntry?> = Property<CodeVisionEntry?>(null)
  val lensPopupActive: Property<Boolean> = Property(false)

  val moreEntry: AdditionalCodeVisionEntry = AdditionalCodeVisionEntry(MORE_PROVIDER_ID, "More...")


  private fun getCodeVisionHost() = CodeVisionInitializer.getInstance(project).getCodeVisionHost()

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
      val id = CodeVisionInitializer.getInstance(project).getCodeVisionHost().getProviderById(entry.providerId)?.groupId ?: entry.providerId
      CodeVisionSettings.instance().setProviderEnabled(id, false)
      CodeVisionInitializer.getInstance(project).getCodeVisionHost().invalidateProviderSignal.fire(CodeVisionHost.LensInvalidateSignal(null))
      return
    }

    if (actionId == HIDE_ALL) {
      CodeVisionSettings.instance().codeVisionEnabled = false
      CodeVisionInitializer.getInstance(project).getCodeVisionHost().invalidateProviderSignal.fire(CodeVisionHost.LensInvalidateSignal(null))
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