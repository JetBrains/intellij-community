// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.actions.impl.LinkAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * The "N/M files" link between the block navigation buttons of the combined diff.
 *
 * The link opens a flat list of the blocks and selects the chosen block in the viewer.
 * The list needs only the block producers, so the action works without a VCS changes tree.
 */
internal class CombinedGoToChangePopupAction(private val model: CombinedDiffModel) : LinkAction(), DumbAware {
  init {
    ActionUtil.copyFrom(this, "GotoChangedFile")
  }

  private val viewer: CombinedDiffViewer? get() = model.context.getUserData(COMBINED_DIFF_VIEWER_KEY)

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val blocks = model.requests
    e.presentation.apply {
      isVisible = true
      isEnabled = blocks.size > 1
      icon = null
      text = DiffBundle.message("diff.files.count.progress", blocks.size, currentIndex(blocks) + 1)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val blocks = model.requests
    val popup = JBPopupFactory.getInstance().createListPopup(BlocksPopupStep(blocks, currentIndex(blocks)))

    val event = e.inputEvent
    if (event is MouseEvent) {
      popup.showUnderneathOf(event.component)
    }
    else {
      popup.showInBestPositionFor(e.dataContext)
    }
  }

  private fun currentIndex(blocks: List<CombinedBlockProducer>): Int {
    val currentId = viewer?.getCurrentBlockId() ?: return -1
    return blocks.indexOfFirst { it.id == currentId }
  }

  private inner class BlocksPopupStep(blocks: List<CombinedBlockProducer>, selectedIndex: Int) :
    BaseListPopupStep<CombinedBlockProducer>(DiffBundle.message("action.presentation.go.to.change.text"), blocks) {
    init {
      defaultOptionIndex = selectedIndex
    }

    override fun getTextFor(value: CombinedBlockProducer): String = value.producer.name

    override fun getIconFor(value: CombinedBlockProducer): Icon? {
      val id = value.id as? CombinedPathBlockId ?: return null
      return FileTypeManager.getInstance().getFileTypeByFileName(id.path.name).icon
    }

    override fun isSpeedSearchEnabled(): Boolean = true

    override fun onChosen(selectedValue: CombinedBlockProducer, finalChoice: Boolean): PopupStep<*>? {
      return doFinalStep {
        viewer?.selectDiffBlock(selectedValue.id, true, CombinedDiffViewer.ScrollPolicy.SCROLL_TO_BLOCK)
      }
    }
  }
}
