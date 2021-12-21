package org.jetbrains.plugins.notebooks.visualization.outputs.impl

import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayManager
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Point
import javax.swing.JComponent
import javax.swing.SwingUtilities

private class OutputCollapsingGutterMouseListener : EditorMouseListener, EditorMouseMotionListener {
  private val EditorMouseEvent.notebookEditor: EditorEx?
    get() = editor.takeIf { NotebookCellInlayManager.get(it) != null }.castSafelyTo()

  override fun mousePressed(e: EditorMouseEvent) {
    val editor = e.notebookEditor ?: return
    val gutterComponentEx = editor.gutterComponentEx

    val point = e.mouseEvent.takeIf { it.component === gutterComponentEx }?.point ?: return
    if (!isAtCollapseVerticalStripe(editor, point)) return
    val component = gutterComponentEx.hoveredCollapsingComponentRect ?: return

    val actionManager = ActionManager.getInstance()
    when (e.mouseEvent.mouseButton) {
      MouseButton.Left -> {
        e.consume()

        val action = actionManager.getAction(NotebookOutputCollapseSingleInCellAction::class.java.simpleName)!!
        actionManager.tryToExecute(action, e.mouseEvent, component, ActionPlaces.EDITOR_GUTTER_POPUP, true)

        SwingUtilities.invokeLater {  // Being invoked without postponing, it would access old states of layouts and get the same results.
          if (!editor.isDisposed) {
            updateState(editor, point)
          }
        }
      }
      MouseButton.Right -> {
        e.consume()

        val group = actionManager.getAction("NotebookOutputCollapseActions")
        if (group is ActionGroup) {
          val menu = actionManager.createActionPopupMenu(ActionPlaces.EDITOR_GUTTER_POPUP, group)
          menu.setTargetComponent(component)
          menu.component.show(gutterComponentEx, e.mouseEvent.x, e.mouseEvent.y)
        }
      }
      else -> Unit
    }
  }

  override fun mouseExited(e: EditorMouseEvent) {
    val editor = e.notebookEditor ?: return
    updateState(editor, null)
  }

  override fun mouseMoved(e: EditorMouseEvent) {
    val editor = e.notebookEditor ?: return
    updateState(editor, e.mouseEvent.point)
  }

  private fun updateState(editor: EditorEx, point: Point?) {
    val gutterComponentEx = editor.gutterComponentEx
    if (point == null || !isAtCollapseVerticalStripe(editor, point)) {
      IdeGlassPaneImpl.forgetPreProcessedCursor(gutterComponentEx)
      gutterComponentEx.cursor = @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") null  // Huh? It's a valid operation!
      updateHoveredComponent(gutterComponentEx, null)
    }
    else {
      val collapsingComponent = getCollapsingComponent(editor, point)
      updateHoveredComponent(gutterComponentEx, collapsingComponent)
      if (collapsingComponent != null) {
        val cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        IdeGlassPaneImpl.savePreProcessedCursor(gutterComponentEx, cursor)
        gutterComponentEx.cursor = cursor
      }
      else {
        IdeGlassPaneImpl.forgetPreProcessedCursor(gutterComponentEx)
      }
    }
  }

  private fun isAtCollapseVerticalStripe(editor: EditorEx, point: Point): Boolean =
    CollapsingComponent.collapseRectHorizontalLeft(editor).let {
      point.x in it until it + CollapsingComponent.COLLAPSING_RECT_WIDTH
    }

  private fun getCollapsingComponent(editor: EditorEx, point: Point): CollapsingComponent? {
    val surroundingX = if ((editor as EditorImpl).isMirrored) 80 else 0
    val surroundingComponent: SurroundingComponent =
      editor.contentComponent.getComponentAt(surroundingX, point.y)
        .castSafelyTo<JComponent>()
        ?.takeIf { it.componentCount > 0 }
        ?.getComponent(0)
        ?.castSafelyTo()
      ?: return null

    val innerComponent: InnerComponent =
      (surroundingComponent.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER).castSafelyTo()
      ?: return null

    val y = point.y - SwingUtilities.convertPoint(innerComponent, 0, 0, editor.contentComponent).y

    val collapsingComponent: CollapsingComponent =
      innerComponent.getComponentAt(0, y).castSafelyTo()
      ?: return null

    if (!collapsingComponent.isWorthCollapsing) return null
    return collapsingComponent
  }

  private fun updateHoveredComponent(gutterComponentEx: EditorGutterComponentEx, collapsingComponent: CollapsingComponent?) {
    val old = gutterComponentEx.hoveredCollapsingComponentRect
    if (old !== collapsingComponent) {
      gutterComponentEx.hoveredCollapsingComponentRect = collapsingComponent
      gutterComponentEx.repaint()
    }
  }
}