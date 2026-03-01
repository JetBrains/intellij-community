package com.intellij.notebooks.visualization.outputs.impl

import com.intellij.notebooks.visualization.outputs.NotebookLazyOutputComponent
import com.intellij.notebooks.visualization.outputs.action.NotebookOutputCollapseSingleInCellAction
import com.intellij.notebooks.visualization.r.inlays.ResizeController
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.IdeBorderFactory
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Dimension
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
  var initialSize: Dimension? = null
  var customSize: Dimension? = null
    set(value) {
      if (field != value) {
        field = value
        revalidate()
      }
    }

  var maximized: Boolean = false

  fun calculateInnerSize(): Dimension =
    size.let { Dimension(it.width - insets.run { left + right }, it.height - insets.run { top + bottom }) }

  val hasBeenManuallyResized: Boolean
    get() = customSize != null

  private val resizeController by lazy {
    ResizeController(this, editor) { _, dy ->
      customSize = (customSize ?: calculateInnerSize()).let {
        Dimension(it.width, max(it.height + dy, editor.lineHeight))
      }
    }.apply {
      resizeStateDispatcher.addListener { state ->
        (border as? CollapsingComponentBorder)?.resized = state != ResizeController.ResizeState.NONE
        repaint()
      }
    }
  }

  val mainComponent: JComponent = child

  private val stubComponent = lazy {
    val result = StubComponent()
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
    revalidate()
  }

  override fun getPreferredSize(): Dimension {
    val result = when {
      !isSeen -> stubComponent.value.preferredSize
      maximized -> mainComponent.preferredSize
      customSize != null -> customSize!!
      shouldUseInitialSize() -> initialSize ?: mainComponent.preferredSize
      else -> mainComponent.preferredSize
    }
    return result.let { Dimension(it.width + insets.run { left + right }, it.height + insets.run { top + bottom }) }
  }

  private fun shouldUseInitialSize(): Boolean = (mainComponent as? NotebookLazyOutputComponent)?.ready == false


  override fun doLayout() {
    val size = calculateInnerSize()
    when {
      !isSeen ->
        stubComponent.value.setBounds(0, 0, size.width, size.height)
      else ->
        mainComponent.setBounds(0, 0, size.width, size.height)
    }
  }

  fun updateStubIfCollapsed() {
    if (!isSeen) {
      stubComponent.value.text = collapsedTextSupplier()
    }
  }

  private class StubComponent : JLabel("...") {
    init {
      isOpaque = true
      border = IdeBorderFactory.createEmptyBorder(JBUI.insets(7, 0))
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          onClick(e)
        }
      })
    }

    private fun onClick(e: MouseEvent) {
      if (e.isConsumed) return
      val parent = parent as? CollapsingComponent ?: return
      val action = ActionUtil.getAction(NotebookOutputCollapseSingleInCellAction::class.java.simpleName)!!
      if (ActionManager.getInstance().tryToExecute(action, e, parent, null, true).isProcessed) {
        e.consume()
      }
    }
  }
}