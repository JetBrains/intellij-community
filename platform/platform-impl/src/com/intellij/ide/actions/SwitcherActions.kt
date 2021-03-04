// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.actions.Switcher.SwitcherPanel
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import java.awt.event.*
import java.util.function.Consumer
import javax.swing.AbstractAction
import javax.swing.JList

internal class ShowRecentFilesAction : LightEditCompatible, SwitcherRecentFilesAction(false)
internal class ShowRecentlyEditedFilesAction : SwitcherRecentFilesAction(true)
internal abstract class SwitcherRecentFilesAction(val onlyEditedFiles: Boolean) : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = event.project != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    Switcher.SWITCHER_KEY.get(project)?.cbShowOnlyEditedFiles?.apply { isSelected = !isSelected } ?: run {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.recent.files")
      SwitcherPanel(project, message("title.popup.recent.files"), onlyEditedFiles, null)
    }
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


internal class SwitcherKeyReleaseListener(event: InputEvent?, val consumer: Consumer<InputEvent>) : KeyAdapter() {
  private val wasAltDown = true == event?.isAltDown
  private val wasAltGraphDown = true == event?.isAltGraphDown
  private val wasControlDown = true == event?.isControlDown
  private val wasMetaDown = true == event?.isMetaDown
  val isEnabled = wasAltDown || wasAltGraphDown || wasControlDown || wasMetaDown
  val isBackwardMove = isEnabled && true == event?.isShiftDown

  val initialModifiers = if (!isEnabled) null
  else StringBuilder().apply {
    if (wasAltDown) append("alt ")
    if (wasAltGraphDown) append("altGraph ")
    if (wasControlDown) append("control ")
    if (wasMetaDown) append("meta ")
  }.toString()

  val forbiddenMnemonic = (event as? KeyEvent)?.keyCode?.let {
    when (it) {
      in KeyEvent.VK_0..KeyEvent.VK_9 -> it.toChar().toString()
      in KeyEvent.VK_A..KeyEvent.VK_Z -> it.toChar().toString()
      else -> null
    }
  }

  fun getShortcuts(vararg keys: String): CustomShortcutSet {
    val modifiers = initialModifiers ?: return CustomShortcutSet.fromString(*keys)
    return CustomShortcutSet.fromStrings(keys.map { modifiers + it })
  }

  override fun keyReleased(keyEvent: KeyEvent) {
    when (keyEvent.keyCode) {
      KeyEvent.VK_ALT -> if (wasAltDown) consumer.accept(keyEvent)
      KeyEvent.VK_ALT_GRAPH -> if (wasAltGraphDown) consumer.accept(keyEvent)
      KeyEvent.VK_CONTROL -> if (wasControlDown) consumer.accept(keyEvent)
      KeyEvent.VK_META -> if (wasMetaDown) consumer.accept(keyEvent)
    }
  }
}
