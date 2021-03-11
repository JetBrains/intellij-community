// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle.message
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.colors.EditorFontType.PLAIN
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.OpenMode
import com.intellij.openapi.keymap.KeymapUtil.getShortcutText
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil.naturalCompare
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowEventSource
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.ui.CellRendererPanel
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.paint.EffectPainter.LINE_UNDERSCORE
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.speedSearch.SpeedSearchUtil.applySpeedSearchHighlighting
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon
import javax.swing.JList
import javax.swing.ListCellRenderer

private fun shortcutText(actionId: String) = ActionManager.getInstance().getKeyboardShortcut(actionId)?.let { getShortcutText(it) }


internal interface SwitcherListItem {
  val separatorAbove: Boolean get() = false

  val textAtLeft: String
  val textAtRight: String? get() = null

  fun getIconAtLeft(selected: Boolean): Icon? = null
  fun getIconAtRight(selected: Boolean): Icon? = null

  fun navigate(switcher: Switcher.SwitcherPanel, mode: OpenMode) = Unit
  fun close(switcher: Switcher.SwitcherPanel) = Unit
}


internal class SwitcherRecentLocations(val switcher: Switcher.SwitcherPanel) : SwitcherListItem {
  override val separatorAbove = true
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
  var mnemonic: String? = null

  override val textAtLeft = window.stripeTitle
  override val textAtRight = if (shortcut) shortcutText(actionId) else null
  override fun getIconAtLeft(selected: Boolean): Icon = MnemonicIcon(window.icon, mnemonic, selected)

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


internal class SwitcherListRenderer(val switcher: Switcher.SwitcherPanel) : ListCellRenderer<SwitcherListItem> {
  private val SEPARATOR = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
  private val SHORTCUT = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Tooltip.shortcutForeground())
  private val left = SimpleColoredComponent().apply { isOpaque = false }
  private val right = SimpleColoredComponent().apply { isOpaque = false }
  private val panel = CellRendererPanel().apply {
    layout = BorderLayout()
    add(BorderLayout.WEST, left)
    add(BorderLayout.EAST, right)
  }

  override fun getListCellRendererComponent(list: JList<out SwitcherListItem>, value: SwitcherListItem?, index: Int,
                                            selected: Boolean, focused: Boolean): Component {
    left.clear()
    right.clear()

    val border = JBUI.Borders.empty(0, 10)
    panel.border = when (!selected && value?.separatorAbove == true) {
      true -> JBUI.Borders.compound(border, JBUI.Borders.customLine(SEPARATOR, 1, 0, 0, 0))
      else -> border
    }
    val item = value ?: return panel
    RenderingUtil.getForeground(list, selected).let {
      left.foreground = it
      right.foreground = it
    }

    left.icon = item.getIconAtLeft(selected)
    right.icon = item.getIconAtRight(selected)

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


private class MnemonicIcon(val icon: Icon?, val mnemonic: String?, val selected: Boolean) : Icon {
  private val size = JBUI.scale(16)
  private val width = mnemonic?.let { JBUI.scale(10) } ?: 0

  override fun getIconWidth() = size + width
  override fun getIconHeight() = size
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    RenderingUtil.getIcon(icon, selected)?.let {
      val dx = x + (size - it.iconWidth) / 2
      val dy = y + (size - it.iconHeight) / 2
      it.paintIcon(c, g, dx, dy)
    }
    if (g is Graphics2D && false == mnemonic?.isEmpty()) {
      val font = PLAIN.globalFont.deriveFont(.8f * size)
      g.font = font
      g.paint = when (selected) {
        true -> JBUI.CurrentTheme.List.foreground(true, true)
        else -> JBUI.CurrentTheme.ActionsList.MNEMONIC_FOREGROUND
      }
      UISettings.setupAntialiasing(g)
      val metrics = g.fontMetrics
      val w = metrics.stringWidth(mnemonic)
      val dx = x + size - (w - width) / 2
      val dy = y + size - metrics.descent
      g.drawString(mnemonic, dx, dy)
      if (SystemInfo.isWindows) {
        LINE_UNDERSCORE.paint(g, dx, dy, w, metrics.descent, font)
      }
    }
  }
}
