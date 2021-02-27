// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.actions.Switcher.SwitcherPanel
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import java.awt.event.ActionEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.AbstractAction
import javax.swing.JList

internal class ShowRecentFilesAction : LightEditCompatible, SwitcherRecentFilesAction(false)
internal class ShowRecentlyEditedFilesAction : SwitcherRecentFilesAction(true)
internal abstract class SwitcherRecentFilesAction(val onlyEditedFiles: Boolean) : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    val project = event.project
    event.presentation.isEnabledAndVisible = project != null && Switcher.SWITCHER_KEY.get(project) == null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.recent.files")
    SwitcherPanel(project, message("title.popup.recent.files"), onlyEditedFiles, null)
  }
}


internal class SwitcherIterateThroughItemsAction : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = Switcher.SWITCHER_KEY.get(event.project) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    Switcher.SWITCHER_KEY.get(event.project)?.go(event.inputEvent)
  }
}


internal class SwitcherToggleOnlyEditedFilesAction : DumbAwareToggleAction() {
  private fun getCheckBox(event: AnActionEvent) =
    Switcher.SWITCHER_KEY.get(event.project)?.cbShowOnlyEditedFiles

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = getCheckBox(event) != null
  }

  override fun isSelected(event: AnActionEvent) = getCheckBox(event)?.isSelected ?: false
  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    getCheckBox(event)?.isSelected = selected
  }
}


internal class SwitcherListFocusAction(val fromList: JList<*>, val toList: JList<*>, vararg listActionIds: String)
  : FocusListener, AbstractAction() {

  override fun actionPerformed(event: ActionEvent) {
    if (toList.isShowing) toList.requestFocusInWindow()
  }

  override fun focusLost(event: FocusEvent) = Unit
  override fun focusGained(event: FocusEvent) {
    val size = toList.model.size
    if (size > 0) {
      val fromIndex = fromList.selectedIndex
      when {
        fromIndex >= 0 -> toIndex = fromIndex.coerceAtMost(size - 1)
        toIndex < 0 -> toIndex = 0
      }
    }
  }

  private var toIndex: Int
    get() = toList.selectedIndex
    set(index) {
      fromList.clearSelection()
      toList.selectedIndex = index
      toList.ensureIndexIsVisible(index)
    }

  init {
    listActionIds.forEach { fromList.actionMap.put(it, this) }
    toList.addFocusListener(this)
  }
}
