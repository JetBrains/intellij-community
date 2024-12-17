package com.intellij.notebooks.visualization.outputs.impl

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.r.inlays.ResizeController
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.IdeBorderFactory
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.Integer.max
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

open class CollapsingComponent(
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
      customHeight = max(customHeight, editor.lineHeight) // We will not allow resizing component below editor line height, because it impossible to resize it back.
      setSize(width, customHeight)
      mainComponent.revalidate()
    }.apply {
      resizeStateDispatcher.addListener { state ->
        (border as? CollapsingComponentBorder)?.resized = state != ResizeController.ResizeState.NONE
        repaint()
      }
    }
  }

  val mainComponent: JComponent = child

  private val stubComponent = lazy {
    val result = StubComponent(editor)
    add(result)
    result
  }

  var isSeen: Boolean
    get() = mainComponent.isVisible
    set(value) {
      mainComponent.isVisible = value
      if (stubComponent.isInitialized()) {
        stubComponent.value.isVisible = !value
      }

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
        stubComponent.value.text = collapsedTextSupplier()
      }
    }

  val hasBeenManuallyResized: Boolean get() = customHeight != -1

  init {
    add(child)
    border = if (resizable) CollapsingComponentBorder(editor) else null
    isSeen = true
    isOpaque = false
  }

  override fun remove(index: Int) {
    thisLogger().error("Components should not be deleted from $this", Throwable())
    super.remove(index)
  }

  fun resetCustomHeight() {
    customHeight = -1
    if (mainComponent.isValid) {
      mainComponent.revalidate()
    }
  }

  override fun getPreferredSize(): Dimension {
    val result = when {
      !isSeen -> stubComponent.value.preferredSize
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
        stubComponent.value.setBounds(0, 0, width - borderWidth, height - borderHeight)

      customHeight >= 0 ->
        mainComponent.setBounds(0, 0, width - borderWidth, customHeight - borderHeight)

      else ->
        mainComponent.setBounds(0, 0, width - borderWidth, height - borderHeight)
    }
  }

  fun updateStubIfCollapsed() {
    if (!isSeen) {
      stubComponent.value.text = collapsedTextSupplier()
    }
  }

  private class StubComponent(private val editor: EditorImpl) : JLabel("...") {
    init {
      isOpaque = true
      border = IdeBorderFactory.createEmptyBorder(JBUI.insets(7, 0))
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
      background = editor.notebookAppearance.getTextOutputBackground(editor.colorsScheme)
      font = EditorUtil.fontForChar(text.first(), fontType, editor).font
    }
  }
}