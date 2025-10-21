// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class ProjectCodeVisionModel(val project: Project) {
  companion object {
    fun getInstance(project: Project): ProjectCodeVisionModel = project.service()
    const val MORE_PROVIDER_ID: String = "!More"
    const val HIDE_PROVIDER_ID: String = "!Hide"
    const val HIDE_ALL: String = "!HideAll"
  }
  
  val maxVisibleLensCount: ViewableMap<CodeVisionAnchorKind, Int> = ViewableMap()
  val lensPopupActive: Property<Boolean> = Property(false)
  val moreEntry: AdditionalCodeVisionEntry = AdditionalCodeVisionEntry(MORE_PROVIDER_ID, CodeVisionMessageBundle.message("more"))

  protected fun getCodeVisionHost(): CodeVisionHost = CodeVisionInitializer.getInstance(project).getCodeVisionHost()

  fun handleLensClick(editor: Editor, range: TextRange, anchorInlay: Inlay<*>, entry: CodeVisionEntry) {
    if (entry.providerId == MORE_PROVIDER_ID) {
      showMore(anchorInlay)
      return
    }

    getCodeVisionHost().handleLensClick(editor, range, entry)
  }

  fun handleLensRightClick(clickedEntry: CodeVisionEntry, anchorInlay: Inlay<*>) {
    showContextPopup(clickedEntry, anchorInlay)
  }

  open fun handleLensExtraAction(editor: Editor, range: TextRange, entry: CodeVisionEntry, actionId: String) {
    if (actionId == HIDE_PROVIDER_ID) {
      val id = CodeVisionInitializer.getInstance(project).getCodeVisionHost().getProviderById(entry.providerId)?.groupId ?: entry.providerId
      CodeVisionSettings.getInstance().setProviderEnabled(id, false)
      CodeVisionInitializer.getInstance(project).getCodeVisionHost().invalidateProviderSignal.fire(
        CodeVisionHost.LensInvalidateSignal(null))
      return
    }

    if (actionId == HIDE_ALL) {
      CodeVisionSettings.getInstance().codeVisionEnabled = false
      CodeVisionInitializer.getInstance(project).getCodeVisionHost().invalidateProviderSignal.fire(
        CodeVisionHost.LensInvalidateSignal(null))
      return
    }

    getCodeVisionHost().handleLensExtraAction(editor, range, entry, actionId)
  }

  fun getLensIndex(lens: CodeVisionEntry): Int {
    return getCodeVisionHost().getNumber(lens.providerId) + 1
  }

  private fun showMore(anchorInlay: Inlay<*>) {
    val model = anchorInlay.getUserData(CodeVisionListData.KEY) ?: return

    CodeVisionPopup.showMorePopup(model.lifetime, anchorInlay, moreEntry, lensPopupActive,
                                  CodeVisionPopup.Disposition.MOUSE_POPUP_DISPOSITION, model.rangeCodeVisionModel, project)
  }

  private fun showContextPopup(clickedEntry: CodeVisionEntry, anchorInlay: Inlay<*>) {

    if (clickedEntry.providerId == MORE_PROVIDER_ID) {
      showMore(anchorInlay)
      return
    }

    val model = anchorInlay.getUserData(CodeVisionListData.KEY) ?: return

    CodeVisionPopup.showContextPopup(model.lifetime, anchorInlay, clickedEntry, lensPopupActive,
                                     CodeVisionPopup.Disposition.MOUSE_POPUP_DISPOSITION, model, project)
  }
}