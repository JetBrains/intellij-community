// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

// TODO: probably it would be better to rework it from the action to some component in order to avoid delayed updates
internal class FilesLabelAction(private val goToChangeAction: AnAction?,
                                private val toolbar: Component?,
                                private val blockState: BlockState) : ToolbarLabelAction() {
  init {
    goToChangeAction?.let(::copyFrom)
    templatePresentation.icon = null
    templatePresentation.description = null // disable label tooltip
  }

  override fun actionPerformed(e: AnActionEvent) {
    goToChangeAction?.actionPerformed(e)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).apply {
      font = JBUI.Fonts.label() // regular font size
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    goToChangeAction?.update(e)

    val statusMessage = buildMessage()

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
      if (goToChangeAction == null) return

      val place = (toolbar as? ActionToolbarImpl)?.place ?: ActionPlaces.DIFF_TOOLBAR
      val event = AnActionEvent.createFromAnAction(goToChangeAction, e.inputEvent, place, ActionToolbar.getDataContextFor(toolbar))
      ActionUtil.performAction(goToChangeAction, event)
    }
  }

  private fun buildMessage(): @Nls String {
    val filesCount = blockState.blocksCount
    val current = blockState.indexOf(blockState.currentBlock) + 1

    val message = DiffBundle.message("combined.diff.files.count", filesCount, current)
    return HtmlBuilder().appendRaw(message).toString()
  }
}