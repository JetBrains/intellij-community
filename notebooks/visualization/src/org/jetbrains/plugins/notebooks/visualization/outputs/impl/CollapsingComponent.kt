package org.jetbrains.plugins.notebooks.visualization.outputs.impl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.IdeBorderFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.notebooks.ui.visualization.notebookAppearance
import org.jetbrains.plugins.notebooks.visualization.outputs.hoveredCollapsingComponentRect
import org.jetbrains.plugins.notebooks.visualization.r.inlays.ResizeController
import org.jetbrains.plugins.notebooks.visualization.r.ui.UiCustomizer
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class CollapsingComponent(
  internal val editor: EditorImpl,
  child: JComponent,
  internal val resizable: Boolean,
  private val collapsedTextSupplier: () -> @NlsSafe String,
) : JPanel(null) {
  private var customHeight: Int = -1

  private val resizeController by lazy {
    ResizeController(this, editor) { _, dy ->
      if (customHeight < 0) {
        customHeight = height - insets.run { top + bottom }
      }
      customHeight += dy
      mainComponent.revalidate()
    }
  }

  var isSeen: Boolean
    get() = mainComponent.isVisible
    set(value) {
      mainComponent.isVisible = value
      stubComponent.isVisible = !value

      if (resizable) {
        if (value) {
          addMouseListener(resizeController)
          addMouseMotionListener(resizeController)
        }
        else {
          removeMouseListener(resizeController)
          removeMouseMotionListener(resizeController)
        }
      }

      if (!value) {
        (stubComponent as StubComponent).text = collapsedTextSupplier()
      }
    }

  init {
    add(child)
    add(StubComponent(editor))
    border = ResizeHandlebarUpdater.invisibleResizeBorder
    isSeen = true
  }

  override fun updateUI() {
    super.updateUI()
    isOpaque = false
  }

  override fun remove(index: Int) {
    LOG.error("Components should not be deleted from $this", Throwable())
    super.remove(index)
  }

  val mainComponent: JComponent get() = getComponent(0) as JComponent
  private val stubComponent: JComponent get() = getComponent(1) as JComponent

  val isWorthCollapsing: Boolean get() = !isSeen || mainComponent.height >= MIN_HEIGHT_TO_COLLAPSE

  val hasBeenManuallyResized: Boolean get() = customHeight != -1

  fun resetCustomHeight() {
    customHeight = -1
    if (mainComponent.isValid) {
      mainComponent.revalidate()
    }
  }

  override fun getPreferredSize(): Dimension {
    val result = when {
      !isSeen -> stubComponent.preferredSize
      customHeight >= 0 -> mainComponent.preferredSize.apply { height = customHeight }
      else -> mainComponent.preferredSize
    }
    result.height += insets.run { top + bottom }
    return result
  }

  override fun doLayout() {
    val (borderWidth, borderHeight) = insets.run { left + right to top + bottom }
    when {
      !isSeen ->
        stubComponent.setBounds(0, 0, width - borderWidth, height - borderHeight)

      customHeight >= 0 ->
        mainComponent.setBounds(0, 0, width - borderWidth, customHeight - borderHeight)

      else ->
        mainComponent.setBounds(0, 0, width - borderWidth, height - borderHeight)
    }
  }

  fun paintGutter(editor: EditorEx, yOffset: Int, g: Graphics) {
    val notebookAppearance = editor.notebookAppearance
    val backgroundColor = notebookAppearance.getCodeCellBackground(editor.colorsScheme)
    if (backgroundColor != null && isWorthCollapsing) {
      val x = collapseRectHorizontalLeft(editor)

      val (rectTop, rectHeight) = insets.let {
        yOffset + y + it.top to height - it.top - it.bottom
      }

      g.color = backgroundColor
      if (editor.gutterComponentEx.hoveredCollapsingComponentRect === this) {
        g.fillRect(x, rectTop, COLLAPSING_RECT_WIDTH, rectHeight)
      }

      if (!isSeen) {
        val outputAdjacentRectWidth = notebookAppearance.getLeftBorderWidth()
        g.color = UiCustomizer.instance.getTextOutputBackground(editor)
        g.fillRect(
          editor.gutterComponentEx.width - outputAdjacentRectWidth,
          rectTop,
          outputAdjacentRectWidth,
          rectHeight,
        )
      }

      val icon = if (isSeen) UIUtil.getTreeExpandedIcon() else UIUtil.getTreeCollapsedIcon()
      val iconOffset = (COLLAPSING_RECT_WIDTH - icon.iconWidth) / 2

      // +1 -- just because the icons are not centered.
      icon.paintIcon(this, g, x + iconOffset + 1, yOffset + y + COLLAPSING_RECT_MARGIN_Y_BOTTOM + iconOffset)
    }
  }

  fun updateStubIfCollapsed() {
    if (!isSeen) {
      (stubComponent as StubComponent).text = collapsedTextSupplier()
    }
  }

  private class StubComponent(private val editor: EditorImpl) : JLabel("...") {
    init {
      border = IdeBorderFactory.createEmptyBorder(Insets(7, 0, 7, 0))
      updateUIFromEditor()
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          onClick(e)
        }
      })
    }

    override fun updateUI() {
      super.updateUI()
      isOpaque = true
      if (@Suppress("SENSELESS_COMPARISON") (editor != null)) {
        updateUIFromEditor()
      }
    }

    private fun onClick(e: MouseEvent) {
      if (e.isConsumed) return
      val parent = parent as? CollapsingComponent ?: return
      val actionManager = ActionManager.getInstance()
      val action = actionManager.getAction(NotebookOutputCollapseSingleInCellAction::class.java.simpleName)!!
      if (actionManager.tryToExecute(action, e, parent, null, true).isProcessed) {
        e.consume()
      }
    }

    private fun updateUIFromEditor() {
      val fontType = editor.colorsScheme.getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES)?.fontType ?: Font.PLAIN
      foreground = JBUI.CurrentTheme.ActionsList.MNEMONIC_FOREGROUND
      background = UiCustomizer.instance.getTextOutputBackground(editor)
      font = EditorUtil.fontForChar(text.first(), fontType, editor).font
    }
  }

  companion object {
    const val MIN_HEIGHT_TO_COLLAPSE = 50
    const val COLLAPSING_RECT_WIDTH = 22
    private const val COLLAPSING_RECT_MARGIN_Y_BOTTOM = 5

    private val LOG by lazy { logger<CollapsingComponent>() }

    @JvmStatic
    fun collapseRectHorizontalLeft(editor: EditorEx): Int =
      (editor.gutterComponentEx.width
       - COLLAPSING_RECT_WIDTH
       - editor.notebookAppearance.LINE_NUMBERS_MARGIN
       - editor.notebookAppearance.CODE_CELL_LEFT_LINE_PADDING)
  }
}