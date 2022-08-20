// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBColor.namedColor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.RowGridLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.RegionPaintIcon
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.RenderingHints.KEY_ANTIALIASING
import java.awt.RenderingHints.VALUE_ANTIALIAS_ON
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.*

private val ASSIGNED_FOREGROUND = namedColor("BookmarkMnemonicAssigned.buttonForeground", 0x000000, 0xBBBBBB)
private val ASSIGNED_BACKGROUND = namedColor("BookmarkMnemonicAssigned.buttonBackground", 0xF7C777, 0x665632)
private val ASSIGNED_BORDER = namedColor("BookmarkMnemonicAssigned.borderColor", ASSIGNED_BACKGROUND)

private val CURRENT_FOREGROUND = namedColor("BookmarkMnemonicCurrent.buttonForeground", 0xFFFFFF, 0xFEFEFE)
private val CURRENT_BACKGROUND = namedColor("BookmarkMnemonicCurrent.buttonBackground", 0x389FD6, 0x345F85)
private val CURRENT_BORDER = namedColor("BookmarkMnemonicCurrent.borderColor", CURRENT_BACKGROUND)

private val SHARED_CURSOR by lazy { Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) }
private val SHARED_LAYOUT by lazy {
  object : RowGridLayout(0, 4, 2, SwingConstants.CENTER) {
    override fun getCellSize(sizes: List<Dimension>) = Dimension(JBUI.scale(24), JBUI.scale(28))
  }
}

internal class BookmarkTypeChooser(
  private var current: BookmarkType?,
  private val assigned: Set<BookmarkType>,
  private var description: String?,
  private val onChosen: (BookmarkType, String) -> Unit
): JPanel() {
  private val bookmarkLayoutGrid = BookmarkLayoutGrid(
    current,
    assigned,
    { current = it },
    { save() }
  )
  private lateinit var descriptionField: JBTextField

  val firstButton = bookmarkLayoutGrid.buttons().first()

  init {
    add(panel {
      row {
        comment(message("mnemonic.chooser.comment"), 55)
      }

      row {
        cell(bookmarkLayoutGrid)
          .horizontalAlign(HorizontalAlign.CENTER)
      }

      row {
        descriptionField = textField()
          .horizontalAlign(HorizontalAlign.FILL)
          .applyToComponent {
            text = description ?: ""
            emptyText.text = message("mnemonic.chooser.description")
            addKeyListener(object: KeyListener {
              override fun keyTyped(e: KeyEvent?) = Unit
              override fun keyReleased(e: KeyEvent?) = Unit
              override fun keyPressed(e: KeyEvent?) {
                if (e != null && e.modifiersEx == 0 && e.keyCode == KeyEvent.VK_ENTER) {
                  save()
                }
              }
            })
          }
          .component
      }

      row {
        cell(createLegend(ASSIGNED_BACKGROUND, message("mnemonic.chooser.legend.assigned.bookmark")))
        cell(createLegend(CURRENT_BACKGROUND, message("mnemonic.chooser.legend.current.bookmark")))
      }
    }.apply {
      border = JBUI.Borders.empty(2, 6)
      isFocusCycleRoot = true
      focusTraversalPolicy = object: LayoutFocusTraversalPolicy() {
        override fun accept(aComponent: Component?): Boolean {
          return super.accept(aComponent) && (aComponent !is JButton || aComponent == firstButton)
        }
      }
    })
  }

  private fun createLegend(color: Color, @Nls text: String) = JLabel(text).apply {
    icon = RegionPaintIcon(8) { g, x, y, width, height, _ ->
      g.color = color
      g.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON)
      g.fillOval(x, y, width, height)
    }.withIconPreScaled(false)
  }

  private fun save() {
    current?.let {
      onChosen(it, descriptionField.text)
    }
  }
}

private class BookmarkLayoutGrid(
  current: BookmarkType?,
  private val assigned: Set<BookmarkType>,
  private val onChosen: (BookmarkType) -> Unit,
  private val save: () -> Unit
) : BorderLayoutPanel(), KeyListener {

  companion object {
    const val TYPE_KEY: String = "BookmarkLayoutGrid.Type"
  }

  init {
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
    updateButtons(current)
  }

  fun buttons() = UIUtil.uiTraverser(this).traverse().filter(JButton::class.java)

  fun createButton(type: BookmarkType) = JButton(type.mnemonic.toString()).apply {
    setMnemonic(type.mnemonic)
    putClientProperty("ActionToolbar.smallVariant", true)
    putClientProperty(TYPE_KEY, type)
    addPropertyChangeListener { repaint() }
    addActionListener {
      onChosen(type)
      updateButtons(type)
    }
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released")
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed")
    cursor = SHARED_CURSOR
  }.also {
    it.addKeyListener(this)
  }

  fun updateButtons(current: BookmarkType?) {
    buttons().forEach {
      val type = it.getClientProperty(TYPE_KEY) as? BookmarkType
      when {
        type == current -> {
          it.putClientProperty("JButton.textColor", CURRENT_FOREGROUND)
          it.putClientProperty("JButton.backgroundColor", CURRENT_BACKGROUND)
          it.putClientProperty("JButton.borderColor", CURRENT_BORDER)
        }
        assigned.contains(type) -> {
          it.putClientProperty("JButton.textColor", ASSIGNED_FOREGROUND)
          it.putClientProperty("JButton.backgroundColor", ASSIGNED_BACKGROUND)
          it.putClientProperty("JButton.borderColor", ASSIGNED_BORDER)
        }
        else -> {
          it.putClientProperty("JButton.textColor", UIManager.getColor("BookmarkMnemonicAvailable.buttonForeground"))
          it.putClientProperty("JButton.backgroundColor", UIManager.getColor("BookmarkMnemonicAvailable.buttonBackground"))
          it.putClientProperty("JButton.borderColor", UIManager.getColor("BookmarkMnemonicAvailable.borderColor"))
        }
      }
    }
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
        KeyEvent.VK_ENTER -> {
          val button = next(event.component, 0, 0) as? JButton
          button?.doClick()
          save()
        }
        else -> {
          val type = BookmarkType.get(event.keyCode.toChar())
          if (type != BookmarkType.DEFAULT) {
            onChosen(type)
            save()
          }
        }
      }
    }
  }
}
