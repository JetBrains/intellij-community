// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.ui.model.ProjectCodeVisionModel.Companion.HIDE_ALL
import com.intellij.codeInsight.codeVision.ui.model.ProjectCodeVisionModel.Companion.HIDE_PROVIDER_ID
import com.intellij.codeInsight.codeVision.ui.model.ProjectCodeVisionModel.Companion.MORE_PROVIDER_ID
import com.intellij.codeInsight.codeVision.ui.popup.CodeVisionPopup
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.ViewableMap

open class ProjectCodeVisionModelImpl(val project: Project) : ProjectCodeVisionModel {
  override val maxVisibleLensCount: ViewableMap<CodeVisionAnchorKind, Int> = ViewableMap()
  override val lensPopupActive: Property<Boolean> = Property(false)
  override val moreEntry: AdditionalCodeVisionEntry = AdditionalCodeVisionEntry(MORE_PROVIDER_ID, CodeVisionBundle.message("more"))


  protected fun getCodeVisionHost() = CodeVisionInitializer.getInstance(project).getCodeVisionHost()

  override fun handleLensClick(editor: Editor, range: TextRange, anchorInlay: Inlay<*>, entry: CodeVisionEntry) {
    if (entry.providerId == MORE_PROVIDER_ID) {
      showMore(anchorInlay)
      return
    }

    getCodeVisionHost().handleLensClick(editor, range, entry)
  }

  override fun handleLensRightClick(clickedEntry: CodeVisionEntry, anchorInlay: Inlay<*>) {
    showContextPopup(clickedEntry, anchorInlay)
  }

  override fun handleLensExtraAction(editor: Editor, range: TextRange, entry: CodeVisionEntry, actionId: String) {
    if (actionId == HIDE_PROVIDER_ID) {
      val id = CodeVisionInitializer.getInstance(project).getCodeVisionHost().getProviderById(entry.providerId)?.groupId ?: entry.providerId
      CodeVisionSettings.instance().setProviderEnabled(id, false)
      CodeVisionInitializer.getInstance(project).getCodeVisionHost().invalidateProviderSignal.fire(
        CodeVisionHost.LensInvalidateSignal(null))
      return
    }

    if (actionId == HIDE_ALL) {
      CodeVisionSettings.instance().codeVisionEnabled = false
      CodeVisionInitializer.getInstance(project).getCodeVisionHost().invalidateProviderSignal.fire(
        CodeVisionHost.LensInvalidateSignal(null))
      return
    }

    getCodeVisionHost().handleLensExtraAction(editor, range, entry, actionId)
  }

  override fun getLensIndex(lens: CodeVisionEntry): Int {
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