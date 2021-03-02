// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle.message
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.OpenMode
import com.intellij.openapi.keymap.KeymapUtil.getShortcutText
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.StringUtil.naturalCompare
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowEventSource
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.ui.*
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.speedSearch.SpeedSearchUtil.applySpeedSearchHighlighting
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Icon
import javax.swing.JList
import javax.swing.ListCellRenderer

private fun shortcutText(actionId: String) = ActionManager.getInstance().getKeyboardShortcut(actionId)?.let { getShortcutText(it) }

internal interface SwitcherListItem {
  val mnemonic: Char? get() = null
  val iconAtLeft: Icon? get() = null
  val textAtLeft: String
  val iconAtRight: Icon? get() = null
  val textAtRight: String? get() = null

  fun navigate(switcher: Switcher.SwitcherPanel, mode: OpenMode) = Unit
  fun close(switcher: Switcher.SwitcherPanel) = Unit
}


internal class SwitcherRecentLocations(val switcher: Switcher.SwitcherPanel) : SwitcherListItem {
  override val textAtLeft: String
    get() = when (switcher.isOnlyEditedFilesShown) {
      true -> message("recent.locations.changed.locations")
      else -> message("recent.locations.popup.title")
    }!!
  override val textAtRight: String?
    get() = when (switcher.isOnlyEditedFilesShown) {
      true -> null
      else -> shortcutText(RecentLocationsAction.RECENT_LOCATIONS_ACTION_ID)
    }

  override fun navigate(switcher: Switcher.SwitcherPanel, mode: OpenMode) {
    RecentLocationsAction.showPopup(switcher.project, switcher.isOnlyEditedFilesShown)
  }
}


internal class SwitcherToolWindow(val window: ToolWindow, shortcut: Boolean) : SwitcherListItem {
  private val actionId = ActivateToolWindowAction.getActionIdForToolWindow(window.id)
  override var mnemonic: Char? = null
  override val iconAtLeft = window.icon ?: AllIcons.FileTypes.UiForm
  override val textAtLeft = window.stripeTitle
  override val textAtRight = if (shortcut) shortcutText(actionId) else null

  override fun navigate(switcher: Switcher.SwitcherPanel, mode: OpenMode) {
    val manager = ToolWindowManager.getInstance(switcher.project) as? ToolWindowManagerImpl
    manager?.activateToolWindow(window.id, null, true, when (switcher.isSpeedSearchPopupActive) {
      true -> ToolWindowEventSource.SwitcherSearch
      else -> ToolWindowEventSource.Switcher
    }) ?: window.activate(null, true)
  }

  override fun close(switcher: Switcher.SwitcherPanel) {
    val manager = ToolWindowManager.getInstance(switcher.project) as? ToolWindowManagerImpl
    manager?.hideToolWindow(window.id, false, false, ToolWindowEventSource.CloseFromSwitcher) ?: window.hide()
  }
}


internal class SwitcherListRenderer(val switcher: Switcher.SwitcherPanel) : ListCellRenderer<Any> {
  private val MNEMONIC = SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, null)
  private val SHORTCUT = SimpleTextAttributes.GRAY_ATTRIBUTES
  private val SEPARATOR = SHORTCUT.fgColor
  private val left = SimpleColoredComponent().apply { isOpaque = false }
  private val right = SimpleColoredComponent().apply { isOpaque = false }
  private val panel = CellRendererPanel().apply {
    layout = BorderLayout()
    add(BorderLayout.WEST, left)
    add(BorderLayout.EAST, right)
  }

  private fun icon(icon: Icon?, selected: Boolean) = icon?.let {
    val size = JBUI.scale(16)
    IconUtil.toSize(when {
                      !selected || StartupUiUtil.isUnderDarcula() -> it
                      else -> IconLoader.getDarkIcon(it, true)
                    }, size, size)
  }

  override fun getListCellRendererComponent(list: JList<out Any>, value: Any?, index: Int,
                                            selected: Boolean, focused: Boolean): Component {
    left.clear()
    right.clear()

    panel.border = when (!selected && value is SwitcherRecentLocations) {
      true -> JBUI.Borders.customLine(SEPARATOR, 1, 0, 0, 0)
      else -> JBUI.Borders.empty()
    }
    val item = value as? SwitcherListItem ?: return panel
    RenderingUtil.getForeground(list, selected).let{
      left.foreground = it
      right.foreground = it
    }

    left.icon = icon(item.iconAtLeft, selected)
    right.icon = icon(item.iconAtRight, selected)

    item.mnemonic?.let {
      if (!switcher.pinned) {
        left.append(it.toString(), MNEMONIC)
        left.append(": ")
      }
    }
    left.append(item.textAtLeft)
    applySpeedSearchHighlighting(switcher, left, false, selected)
    item.textAtRight?.let { right.append(it, SHORTCUT) }

    return panel
  }


  val toolWindows: List<SwitcherToolWindow> by lazy {
    val manager = ToolWindowManager.getInstance(switcher.project)
    val windows = manager.toolWindowIds
      .mapNotNull { manager.getToolWindow(it) }
      .filter { it.isAvailable && it.isShowStripeButton }
      .map { SwitcherToolWindow(it, switcher.pinned) }
      .sortedWith(Comparator { window1, window2 -> naturalCompare(window1.textAtLeft, window2.textAtLeft) })

    // TODO: assign mnemonics

    windows
  }
}
