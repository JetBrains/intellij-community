package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.notebooks.ui.SteadyUIPanel
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.border.AbstractBorder
import javax.swing.plaf.basic.BasicButtonUI

class JupyterCellBorderButton(
  val editor: EditorEx,
  private val action: AnAction
) : JButton() {
  private val sausageUi = SausageButtonUI(editor)

  fun initialize() {
    isVisible = false
    setUI(sausageUi)
    defineButtonAppearance(editor, this, action)
    addActionListener {
      val anActionEvent = AnActionEvent.createFromAnAction(action, null, ActionPlaces.EDITOR_INLAY, editor.dataContext)
      ActionManagerEx.getInstanceEx().tryToExecute(action, anActionEvent.inputEvent, this, ActionPlaces.EDITOR_INLAY, true)
    }
  }

  /** See [SteadyUIPanel] for explanation. */
  override fun updateUI() {
    // May be null, because this method may be called in a constructor of the super class while this class is not initialized.
    @Suppress("SENSELESS_COMPARISON")
    if (sausageUi != null) {
      setUI(sausageUi)
      defineButtonAppearance(editor, this, action)
    }
  }
}

private fun defineButtonAppearance(editor: Editor, button: JButton, action: AnAction) {
  val notebookAppearance = editor.notebookAppearance
  val text = action.templateText ?: action.toString()
  val shortcut =
    action.shortcutSet.shortcuts.firstOrNull()
      ?.let { it as? KeyboardShortcut }
      ?.let {
        "${KeymapUtil.getKeystrokeText(it.firstKeyStroke)} ${KeymapUtil.getKeystrokeText(it.secondKeyStroke)}".trimEnd()
      }

  val (plainText, prettyText) =
    if (shortcut != null) {
      val shortcutColor =
        notebookAppearance.getSausageButtonShortcutColor(editor.colorsScheme).rgb.let { "#" + Integer.toHexString(it and 0xffffff) }
      "$text $shortcut" to "<html><body><nobr>$text <font color='$shortcutColor'>$shortcut</font></nobr></body></html>"
    }
    else text to text
  button.text = prettyText
  // Hours spent on attempts to make button choosing correct position by ComponentUI: 5.
  button.preferredSize = Dimension(
    button.getFontMetrics(button.font).stringWidth(plainText) + notebookAppearance.CELL_BORDER_HEIGHT,
    notebookAppearance.CELL_BORDER_HEIGHT
  )
}

private fun getRightButton(components: Array<out Component>?) = components?.filterIsInstance<JButton>()?.maxByOrNull { it.x }

private fun getLeftButton(components: Array<out Component>?) = components?.filterIsInstance<JButton>()?.minByOrNull { it.x }

private class SausageBorder(private val editor: Editor) : AbstractBorder() {
  override fun isBorderOpaque(): Boolean = true

  override fun getBorderInsets(c: Component): Insets = JBUI.insetsBottom(1 - c.height % 2)

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, initialWidth: Int, initialHeight: Int) {
    g.color = editor.notebookAppearance.getSausageButtonBorderColor(editor.colorsScheme)

    // Line width is 1 px. Dimensions should be reduced to avoid clipping at bottom and at right.
    val width = initialWidth - 1
    val height = initialHeight - 1

    val components = c.parent?.components
    val leftButton = getLeftButton(components)
    val rightButton = getRightButton(components)
    when {
      components == null || components.size <= 1 -> g.drawRoundRect(x, y, width, height, height, height)
      leftButton === c -> {
        g.drawLine(x, y, x + width + 1, y)
        g.drawLine(x, y + height, x + width + 1, y + height)
        g.drawLine(x, y, x, y + height)
      }
      rightButton === c -> {
        g.drawLine(x, y, x + width, y)
        g.drawLine(x, y, x, y + height)
        g.drawLine(x, y + height, x + width, y + height)
        g.drawLine(x + width, y, x + width, y + height )
      }
      else -> g.drawRect(x, y, width + height, height)
    }
  }
}

private class SausageButtonUI(val editor: Editor) : BasicButtonUI() {
  override fun update(g: Graphics, c: JComponent) {
    c as JupyterCellBorderButton
    c.background = editor.notebookAppearance.getSausageButtonAppearanceBackgroundColor(editor.colorsScheme)
    c.foreground = editor.notebookAppearance.getSausageButtonAppearanceForegroundColor(editor.colorsScheme)

    g.color = c.background

    val x = 0
    val y = 0
    val width = c.width
    val height = c.height
    val components = c.parent?.components
    val leftButton = getLeftButton(components)
    val rightButton = getRightButton(components)
    when {
      components == null || components.size <= 1 -> g.fillRoundRect(x, y, width, height, height, height)
      leftButton === c -> {
        g.fillRect(x, y, width, height)
        g.fillArc(x, y, height, height, 90, 180)
      }
      rightButton === c -> {
        g.fillRect(x, y, width, height)
        g.fillArc(x + width - height, y, height, height, -90, 180)
      }
      else -> g.fillRect(x, y, width, height)
    }
    paint(g, c)
  }

  override fun installUI(c: JComponent) {
    c.border = SausageBorder(editor)
    c.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    c.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    super.installUI(c)
  }
}

class HidingMouseListener(
  private val mainComponent: JComponent,
  private vararg val componentsToHide: Component
) : MouseAdapter() {
  override fun mouseEntered(e: MouseEvent) {
    for (c in componentsToHide) {
      c.isVisible = true
    }
  }

  override fun mouseExited(e: MouseEvent) {
    if (e.point !in mainComponent.bounds) {
      for (c in componentsToHide) {
        c.isVisible = false
      }
    }
  }
}
