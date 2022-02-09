// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl.ui

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.diff.DiffBundle.message
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.ThreeState
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

abstract class DifferencesLabel(private val goToChangeAction: AnAction?, private val toolbar: Component?) : ToolbarLabelAction() {
  init {
    goToChangeAction?.let(::copyFrom)
    templatePresentation.icon = null
    templatePresentation.description = null //disable label tooltip
  }

  override fun actionPerformed(e: AnActionEvent) {
    goToChangeAction?.actionPerformed(e)
  }

  override fun update(e: AnActionEvent) {
    goToChangeAction?.update(e)

    val statusMessage = buildStatusMessage()

    if (statusMessage.isBlank()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
    e.presentation.text = statusMessage
  }

  override fun isCopyable(): Boolean = true

  @Suppress("DialogTitleCapitalization")
  override fun getHyperlinkTooltip(): String = templatePresentation.text

  override fun createHyperlinkListener(): HyperlinkListener = object : HyperlinkAdapter() {

    override fun hyperlinkActivated(e: HyperlinkEvent) {
      if (goToChangeAction != null) {
        val place = (toolbar as? ActionToolbarImpl)?.place ?: ActionPlaces.DIFF_TOOLBAR
        val event = AnActionEvent.createFromAnAction(goToChangeAction, e.inputEvent, place, ActionToolbar.getDataContextFor(toolbar))
        if (ActionUtil.lastUpdateAndCheckDumb(goToChangeAction, event, false)) {
          ActionUtil.performActionDumbAwareWithCallbacks(goToChangeAction, event)
        }
      }
    }
  }

  private fun buildStatusMessage(): @Nls String {
    val differencesMessage = buildDifferencesStatusMessage()
    val filesCountMessage = buildFilesCountStatusMessage(differencesMessage)

    return differencesMessage + filesCountMessage.orEmpty()
  }

  private fun buildFilesCountStatusMessage(differencesStatus: @Nls String?): @Nls String? {
    val filesCount = getFileCount()
    val filesLink = HtmlBuilder().appendLink("", message("diff.files.count.hyperlink.text", filesCount)).toString()
    val inFiles = " " + message("diff.files.count.files.in.text", filesLink)

    return when {
      differencesStatus.isNullOrBlank() && filesCount > 1 -> filesLink
      differencesStatus != null && differencesStatus.isNotBlank() && filesCount > 1 -> inFiles
      else -> null
    }
  }

  private fun buildDifferencesStatusMessage(): @Nls String {
    return DiffUtil.getStatusText(getTotalDifferences(), 0, ThreeState.UNSURE)
  }

  abstract fun getTotalDifferences(): Int
  abstract fun getFileCount(): Int

  interface DifferencesCounter {
    fun getTotalDifferences(): Int
  }
}
