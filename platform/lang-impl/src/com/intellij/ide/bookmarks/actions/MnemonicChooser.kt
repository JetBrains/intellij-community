// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions

import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.bookmarks.BookmarkBundle.message
import com.intellij.ide.bookmarks.BookmarkManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBColor.namedColor
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.RowGridLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.RegionPaintIcon
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.RenderingHints.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.*

private val ASSIGNED_FOREGROUND = namedColor("BookmarkMnemonicAssigned.foreground", 0x000000, 0xBBBBBB)
private val ASSIGNED_BACKGROUND = namedColor("BookmarkMnemonicAssigned.background", 0xF7C777, 0x665632)
private val ASSIGNED_BORDER = namedColor("BookmarkMnemonicAssigned.borderColor", ASSIGNED_BACKGROUND)

private val CURRENT_FOREGROUND = namedColor("BookmarkMnemonicCurrent.foreground", 0xFFFFFF, 0xFEFEFE)
private val CURRENT_BACKGROUND = namedColor("BookmarkMnemonicCurrent.background", 0x389FD6, 0x345F85)
private val CURRENT_BORDER = namedColor("BookmarkMnemonicCurrent.borderColor", CURRENT_BACKGROUND)

private val SHARED_CURSOR by lazy { Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) }
private val SHARED_LAYOUT by lazy {
  object : RowGridLayout(0, 4, 2, SwingConstants.CENTER) {
    override fun getCellSize(sizes: List<Dimension>) = Dimension(JBUI.scale(24), JBUI.scale(28))
  }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("This class will be removed soon", ReplaceWith("com.intellij.ide.bookmark.actions.BookmarkTypeChooser"), DeprecationLevel.ERROR)
internal class MnemonicChooser(
  private val manager: BookmarkManager,
  private val current: BookmarkType?,
  private val onChosen: (BookmarkType) -> Unit
) : BorderLayoutPanel(), KeyListener {

  init {
    isFocusCycleRoot = true
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
    border = JBUI.Borders.empty(2, 6)
    addToLeft(JPanel(SHARED_LAYOUT).apply {
      border = JBUI.Borders.empty(5)
      BookmarkType.values()
        .filter { it.mnemonic.isDigit() }
        .forEach { add(createButton(it)) }
    })
    addToRight(JPanel(SHARED_LAYOUT).apply {
      border = JBUI.Borders.empty(5)
      BookmarkType.values()
        .filter { it.mnemonic.isLetter() }
        .forEach { add(createButton(it)) }
    })
    if (manager.hasBookmarksWithMnemonics()) {
      addToBottom(BorderLayoutPanel().apply {
        border = JBUI.Borders.empty(5, 6, 1, 6)
        addToTop(JSeparator())
        addToBottom(JPanel(HorizontalLayout(12)).apply {
          border = JBUI.Borders.empty(5, 1)
          add(HorizontalLayout.LEFT, createLegend(ASSIGNED_BACKGROUND, message("mnemonic.chooser.legend.assigned.bookmark")))
          if (current != null && current != BookmarkType.DEFAULT) {
            add(HorizontalLayout.LEFT, createLegend(CURRENT_BACKGROUND, message("mnemonic.chooser.legend.current.bookmark")))
          }
        })
      })
    }
  }

  fun buttons() = UIUtil.uiTraverser(this).traverse().filter(JButton::class.java)

  private fun createButton(type: BookmarkType) = JButton(type.mnemonic.toString()).apply {
    setMnemonic(type.mnemonic)
    addActionListener { onChosen(type) }
    putClientProperty("ActionToolbar.smallVariant", true)
    when {
      type == current -> {
        putClientProperty("JButton.textColor", CURRENT_FOREGROUND)
        putClientProperty("JButton.backgroundColor", CURRENT_BACKGROUND)
        putClientProperty("JButton.borderColor", CURRENT_BORDER)
      }
      manager.findBookmarkForMnemonic(type.mnemonic) != null -> {
        putClientProperty("JButton.textColor", ASSIGNED_FOREGROUND)
        putClientProperty("JButton.backgroundColor", ASSIGNED_BACKGROUND)
        putClientProperty("JButton.borderColor", ASSIGNED_BORDER)
      }
      else -> {
        putClientProperty("JButton.textColor", UIManager.getColor("BookmarkMnemonicAvailable.foreground"))
        putClientProperty("JButton.backgroundColor", UIManager.getColor("BookmarkMnemonicAvailable.background"))
        putClientProperty("JButton.borderColor", UIManager.getColor("BookmarkMnemonicAvailable.borderColor"))
      }
    }
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released")
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed")
    cursor = SHARED_CURSOR
  }.also {
    it.addKeyListener(this)
  }

  private fun createLegend(color: Color, @Nls text: String) = JLabel(text).apply {
    icon = RegionPaintIcon(8) { g, x, y, width, height, _ ->
      g.color = color
      g.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON)
      g.fillOval(x, y, width, height)
    }.withIconPreScaled(false)
  }

  private fun offset(delta: Int, size: Int) = when {
    delta < 0 -> delta
    delta > 0 -> delta + size
    else -> size / 2
  }

  private fun next(source: Component, dx: Int, dy: Int): Component? {
    val point = SwingUtilities.convertPoint(source, offset(dx, source.width), offset(dy, source.height), this)
    val component = next(source, dx, dy, point)
    if (component != null || !Registry.`is`("ide.bookmark.mnemonic.chooser.cyclic.scrolling.allowed")) return component
    if (dx > 0) point.x = 0
    if (dx < 0) point.x = dx + width
    if (dy > 0) point.y = 0
    if (dy < 0) point.y = dy + height
    return next(source, dx, dy, point)
  }

  private fun next(source: Component, dx: Int, dy: Int, point: Point): Component? {
    while (contains(point)) {
      val component = SwingUtilities.getDeepestComponentAt(this, point.x, point.y)
      if (component is JButton) return component
      point.translate(dx * source.width / 2, dy * source.height / 2)
    }
    return null
  }

  override fun keyTyped(event: KeyEvent) = Unit
  override fun keyReleased(event: KeyEvent) = Unit
  override fun keyPressed(event: KeyEvent) {
    if (event.modifiersEx == 0) {
      when (event.keyCode) {
        KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> next(event.component, 0, -1)?.requestFocus()
        KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> next(event.component, 0, 1)?.requestFocus()
        KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> next(event.component, -1, 0)?.requestFocus()
        KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> next(event.component, 1, 0)?.requestFocus()
        else -> {
          val type = BookmarkType.get(event.keyCode.toChar())
          if (type != BookmarkType.DEFAULT) onChosen(type)
        }
      }
    }
  }
}
