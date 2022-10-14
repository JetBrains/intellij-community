// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor.namedColor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.RowGridLayout
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.RegionPaintIcon
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.RenderingHints.KEY_ANTIALIASING
import java.awt.RenderingHints.VALUE_ANTIALIAS_ON
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.*

private val ASSIGNED_FOREGROUND = namedColor("Bookmark.MnemonicAssigned.foreground", 0x000000, 0xBBBBBB)
private val ASSIGNED_BACKGROUND = namedColor("Bookmark.MnemonicAssigned.background", 0xF7C777, 0x665632)

private val CURRENT_FOREGROUND = namedColor("Bookmark.MnemonicCurrent.foreground", 0xFFFFFF, 0xFEFEFE)
private val CURRENT_BACKGROUND = namedColor("Bookmark.MnemonicCurrent.background", 0x389FD6, 0x345F85)

private val SHARED_CURSOR by lazy { Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) }
private val SHARED_LAYOUT by lazy {
  object : RowGridLayout(0, 4, 2, SwingConstants.CENTER) {
    override fun getCellSize(sizes: List<Dimension>) = when {
      ExperimentalUI.isNewUI() -> Dimension(JBUI.scale(30), JBUI.scale(34))
      else -> Dimension(JBUI.scale(24), JBUI.scale(28))
    }
  }
}

private object MySpacingConfiguration: IntelliJSpacingConfiguration() {
  override val verticalComponentGap: Int
    get() = 0

  override val verticalSmallGap: Int
    get() = JBUI.scale(8)

  override val verticalMediumGap: Int
    get() = JBUI.scale(if (ExperimentalUI.isNewUI()) 16 else 8)
}

internal class BookmarkTypeChooser(
  private var current: BookmarkType?,
  assigned: Set<BookmarkType>,
  private var description: String?,
  private val onChosen: (BookmarkType, String) -> Unit
) {
  private val bookmarkLayoutGrid = BookmarkLayoutGrid(
    current,
    assigned,
    { if (current == it) save() else current = it },
    { save() }
  )
  private lateinit var descriptionField: JBTextField

  val firstButton = bookmarkLayoutGrid.buttons().first()
  val content: JPanel

  init {
    content = panel {
      customizeSpacingConfiguration(MySpacingConfiguration) {
        row {
          val lineLength = if (ExperimentalUI.isNewUI()) 63 else 55
          comment(message("mnemonic.chooser.comment"), lineLength).applyToComponent {
            if (ExperimentalUI.isNewUI()) border = JBUI.Borders.empty(2, 4, 0, 4)
          }
        }.bottomGap(BottomGap.MEDIUM)

        row {
          cell(bookmarkLayoutGrid)
            .align(AlignX.CENTER)
        }.bottomGap(BottomGap.MEDIUM)

        row {
          descriptionField = textField()
            .align(AlignX.FILL)
            .applyToComponent {
              text = description ?: ""
              emptyText.text = message("mnemonic.chooser.description")
              isOpaque = false
              addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent?) {
                  if (e != null && e.modifiersEx == 0 && e.keyCode == KeyEvent.VK_ENTER) {
                    save()
                  }
                }
              })
            }
            .component
        }.bottomGap(BottomGap.SMALL)

        row {
          cell(createLegend(ASSIGNED_BACKGROUND, message("mnemonic.chooser.legend.assigned.bookmark")))
          cell(createLegend(CURRENT_BACKGROUND, message("mnemonic.chooser.legend.current.bookmark")))
        }
      }
    }.apply {
      border = when {
        ExperimentalUI.isNewUI() -> JBUI.Borders.empty(0, 20, 14, 20)
        else -> JBUI.Borders.empty(12, 11)
      }
      background = JBUI.CurrentTheme.Popup.BACKGROUND
      isFocusCycleRoot = true
      focusTraversalPolicy = object: LayoutFocusTraversalPolicy() {
        override fun accept(aComponent: Component?): Boolean {
          return super.accept(aComponent) && (aComponent !is JButton || aComponent == firstButton)
        }
      }
    }
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
      border = when {
        ExperimentalUI.isNewUI() -> JBUI.Borders.emptyRight(14)
        else -> JBUI.Borders.empty(5)
      }
      isOpaque = false
      BookmarkType.values()
        .filter { it.mnemonic.isDigit() }
        .forEach { add(createButton(it)) }
    })
    addToRight(JPanel(SHARED_LAYOUT).apply {
      border = when {
        ExperimentalUI.isNewUI() -> JBUI.Borders.empty()
        else -> JBUI.Borders.empty(5)
      }
      isOpaque = false
      BookmarkType.values()
        .filter { it.mnemonic.isLetter() }
        .forEach { add(createButton(it)) }
    })
    isOpaque = false
    updateButtons(current)
  }

  fun buttons() = UIUtil.uiTraverser(this).traverse().filter(JButton::class.java)

  fun createButton(type: BookmarkType) = JButton(type.mnemonic.toString()).apply {
    setMnemonic(type.mnemonic)
    isOpaque = false
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

    addKeyListener(this@BookmarkLayoutGrid)
  }

  fun updateButtons(current: BookmarkType?) {
    buttons().forEach {
      val type = it.getClientProperty(TYPE_KEY) as? BookmarkType
      when {
        type == current -> {
          it.putClientProperty("JButton.textColor", CURRENT_FOREGROUND)
          it.putClientProperty("JButton.backgroundColor", CURRENT_BACKGROUND)
          it.putClientProperty("JButton.borderColor", CURRENT_BACKGROUND)
        }
        assigned.contains(type) -> {
          it.putClientProperty("JButton.textColor", ASSIGNED_FOREGROUND)
          it.putClientProperty("JButton.backgroundColor", ASSIGNED_BACKGROUND)
          it.putClientProperty("JButton.borderColor", ASSIGNED_BACKGROUND)
        }
        else -> {
          it.putClientProperty("JButton.textColor", UIManager.getColor("Bookmark.MnemonicAvailable.foreground"))
          it.putClientProperty("JButton.backgroundColor", JBUI.CurrentTheme.Popup.BACKGROUND)
          it.putClientProperty("JButton.borderColor", UIManager.getColor("Bookmark.MnemonicAvailable.borderColor"))
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
