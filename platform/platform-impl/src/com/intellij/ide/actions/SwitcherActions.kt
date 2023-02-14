// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.actions.Switcher.SwitcherPanel
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.util.BitUtil.isSet
import com.intellij.util.ui.accessibility.ScreenReader
import java.awt.event.*
import java.util.function.Consumer
import javax.swing.AbstractAction
import javax.swing.JList
import javax.swing.KeyStroke

private fun forward(event: AnActionEvent) = true != event.inputEvent?.isShiftDown


internal class ShowSwitcherForwardAction : BaseSwitcherAction(true)
internal class ShowSwitcherBackwardAction : BaseSwitcherAction(false)
internal abstract class BaseSwitcherAction(val forward: Boolean?) : DumbAwareAction() {
  private fun isControlTab(event: KeyEvent?) = event?.run { isControlDown && keyCode == KeyEvent.VK_TAB } ?: false
  private fun isControlTabDisabled(event: AnActionEvent) = ScreenReader.isActive() && isControlTab(event.inputEvent as? KeyEvent)

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = event.project != null && !isControlTabDisabled(event)
    event.presentation.isVisible = forward == null
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val switcher = Switcher.SWITCHER_KEY.get(project)
    if (switcher != null && (!switcher.recent || forward != null)) {
      switcher.go(forward ?: forward(event))
    }
    else {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("switcher")
      SwitcherPanel(project, message("window.title.switcher"), event.inputEvent, null, forward ?: forward(event))
    }
  }
}


internal class ShowRecentFilesAction : LightEditCompatible, BaseRecentFilesAction(false)
internal class ShowRecentlyEditedFilesAction : BaseRecentFilesAction(true)
internal abstract class BaseRecentFilesAction(private val onlyEditedFiles: Boolean) : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = event.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    Switcher.SWITCHER_KEY.get(project)?.cbShowOnlyEditedFiles?.apply { isSelected = !isSelected }
    ?: SwitcherPanel(project, message("title.popup.recent.files"), null, onlyEditedFiles, true)
  }
}


internal class SwitcherIterateThroughItemsAction : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = Switcher.SWITCHER_KEY.get(event.project) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(event: AnActionEvent) {
    Switcher.SWITCHER_KEY.get(event.project)?.go(forward(event))
  }
}


internal class SwitcherToggleOnlyEditedFilesAction : DumbAwareToggleAction() {
  private fun getCheckBox(event: AnActionEvent) =
    Switcher.SWITCHER_KEY.get(event.project)?.cbShowOnlyEditedFiles

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = getCheckBox(event) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun isSelected(event: AnActionEvent) = getCheckBox(event)?.isSelected ?: false
  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    getCheckBox(event)?.isSelected = selected
  }
}


internal class SwitcherNextProblemAction : SwitcherProblemAction(true)
internal class SwitcherPreviousProblemAction : SwitcherProblemAction(false)
internal abstract class SwitcherProblemAction(val forward: Boolean) : DumbAwareAction() {
  private fun getFileList(event: AnActionEvent) =
    Switcher.SWITCHER_KEY.get(event.project)?.let { if (it.pinned) it.files else null }

  private fun getErrorIndex(list: JList<SwitcherVirtualFile>): Int? {
    val model = list.model ?: return null
    val size = model.size
    if (size <= 0) return null
    val range = 0 until size
    val start = when (forward) {
      true -> list.leadSelectionIndex.let { if (range.first <= it && it < range.last) it + 1 else range.first }
      else -> list.leadSelectionIndex.let { if (range.first < it && it <= range.last) it - 1 else range.last }
    }
    for (i in range) {
      val index = when (forward) {
        true -> (start + i).let { if (it > range.last) it - size else it }
        else -> (start - i).let { if (it < range.first) it + size else it }
      }
      if (model.getElementAt(index)?.isProblemFile == true) return index
    }
    return null
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = getFileList(event) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val list = getFileList(event) ?: return
    val index = getErrorIndex(list) ?: return
    list.selectedIndex = index
    list.ensureIndexIsVisible(index)
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
    toList.addListSelectionListener {
      if (!fromList.isSelectionEmpty && !toList.isSelectionEmpty) {
        fromList.selectionModel.clearSelection()
      }
    }
  }
}


internal class SwitcherKeyReleaseListener(event: InputEvent?, val consumer: Consumer<InputEvent>) : KeyAdapter() {
  private val wasAltDown = true == event?.isAltDown
  private val wasAltGraphDown = true == event?.isAltGraphDown
  private val wasControlDown = true == event?.isControlDown
  private val wasMetaDown = true == event?.isMetaDown
  val isEnabled = wasAltDown || wasAltGraphDown || wasControlDown || wasMetaDown

  private val initialModifiers = if (!isEnabled) null
  else StringBuilder().apply {
    if (wasAltDown) append("alt ")
    if (wasAltGraphDown) append("altGraph ")
    if (wasControlDown) append("control ")
    if (wasMetaDown) append("meta ")
  }.toString()

  val forbiddenMnemonic = (event as? KeyEvent)?.keyCode?.let { getMnemonic(it) }

  fun getForbiddenMnemonic(keyStroke: KeyStroke) = when {
    isSet(keyStroke.modifiers, InputEvent.ALT_DOWN_MASK) != wasAltDown -> null
    isSet(keyStroke.modifiers, InputEvent.ALT_GRAPH_DOWN_MASK) != wasAltGraphDown -> null
    isSet(keyStroke.modifiers, InputEvent.CTRL_DOWN_MASK) != wasControlDown -> null
    isSet(keyStroke.modifiers, InputEvent.META_DOWN_MASK) != wasMetaDown -> null
    else -> getMnemonic(keyStroke.keyCode)
  }

  private fun getMnemonic(keyCode: Int) = when (keyCode) {
    in KeyEvent.VK_0..KeyEvent.VK_9 -> keyCode.toChar().toString()
    in KeyEvent.VK_A..KeyEvent.VK_Z -> keyCode.toChar().toString()
    else -> null
  }

  fun getShortcuts(vararg keys: String): CustomShortcutSet {
    val modifiers = initialModifiers ?: return CustomShortcutSet.fromString(*keys)
    val list = mutableListOf<String>()
    keys.mapTo(list) { modifiers + it }
    keys.mapTo(list) { modifiers + "shift " + it }
    return CustomShortcutSet.fromStrings(list)
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
