// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions

import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.RowGridLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.KeyStroke.getKeyStroke
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.SwingConstants.CENTER
import javax.swing.SwingUtilities

private const val DIGITS = "1234567890"
private const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private val SELECTED = JBColor(0xfafa8b, 0x675133)

internal open class MnemonicChooser : BorderLayoutPanel(), KeyListener {
  private val layout = object : RowGridLayout(0, 4, 1, CENTER) {
    override fun getCellSize(sizes: List<Dimension>) = super.getCellSize(sizes).also { it.width = it.height }
  }

  init {
    isFocusCycleRoot = true
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
    addToLeft(createButtons(DIGITS))
    addToRight(createButtons(LETTERS))
  }

  private fun buttons() = UIUtil.uiTraverser(this).traverse().filter(JButton::class.java)

  fun getPreferableFocusComponent() = buttons().first()

  protected open fun onMnemonicChosen(mnemonic: Char) = Unit
  protected open fun onCancelled() = Unit
  protected open fun isOccupied(mnemonic: Char) = false

  private fun createButtons(mnemonics: String): JPanel {
    val panel = JPanel(layout)
    panel.border = JBUI.Borders.empty(5)
    mnemonics.forEach { panel.add(createButton(it)) }
    return panel
  }

  private fun createButton(mnemonic: Char): JButton {
    val button = JButton(mnemonic.toString())
    button.setMnemonic(mnemonic)
    button.putClientProperty("ActionToolbar.smallVariant", true)
    button.putClientProperty("JButton.backgroundColor", if (isOccupied(mnemonic)) SELECTED else null)
    button.addActionListener { mnemonicSelected(mnemonic) }
    button.addKeyListener(this)
    button.inputMap.put(getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released")
    button.inputMap.put(getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed")
    return button
  }

  private fun mnemonicSelected(mnemonic: Char) {
    onMnemonicChosen(mnemonic)
  }

  private fun offset(delta: Int, size: Int) = when {
    delta < 0 -> delta
    delta > 0 -> delta + size
    else -> size / 2
  }

  private fun next(source: Component, dx: Int, dy: Int): Component? {
    val point = SwingUtilities.convertPoint(source, offset(dx, source.width), offset(dy, source.height), this)
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
        KeyEvent.VK_ESCAPE -> onCancelled()
        KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> next(event.component, 0, -1)?.requestFocus()
        KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> next(event.component, 0, 1)?.requestFocus()
        KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> next(event.component, -1, 0)?.requestFocus()
        KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> next(event.component, 1, 0)?.requestFocus()
        else -> {
          val mnemonic = event.keyCode.toChar()
          if (DIGITS.indexOf(mnemonic) >= 0 || LETTERS.indexOf(mnemonic) >= 0) {
            mnemonicSelected(mnemonic)
          }
        }
      }
    }
  }
}
