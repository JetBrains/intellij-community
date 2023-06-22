// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import javax.swing.SwingUtilities

@ApiStatus.Internal
interface DocRenderItemManager {
  companion object {
    @JvmStatic
    fun getInstance(): DocRenderItemManager = service()

    private val LISTENERS_DISPOSABLE = Key.create<Disposable>("doc.render.listeners.disposable")
  }

  fun getItemAroundOffset(editor: Editor, offset: Int): DocRenderItem? {
    return null
  }

  fun removeAllItems(editor: Editor)

  fun setItemsToEditor(editor: Editor, itemsToSet: DocRenderPassFactory.Items, collapseNewItems: Boolean)

  fun resetToDefaultState(editor: Editor)

  fun getItems(editor: Editor): Collection<DocRenderItem>?

  fun isRenderedDocHighlighter(highlighter: RangeHighlighter): Boolean

  fun setupListeners(editor: Editor, disable: Boolean) {
    val existingDisposable = editor.getUserData(LISTENERS_DISPOSABLE)
    if (disable) {
      existingDisposable?.let {
        Disposer.dispose(it)
        editor.putUserData(LISTENERS_DISPOSABLE, null)
      }
    }
    else if (existingDisposable == null) {
      val connection = ApplicationManager.getApplication().messageBus.connect()
      editor.putUserData(LISTENERS_DISPOSABLE, connection)
      connection.setDefaultHandler(Runnable { DocRenderItemUpdater.updateRenderers(editor, true) })
      connection.subscribe(EditorColorsManager.TOPIC)
      connection.subscribe(LafManagerListener.TOPIC)
      val selectionManager = DocRenderSelectionManager(editor)
      Disposer.register(connection, selectionManager)
      val mouseEventBridge = DocRenderMouseEventBridge(selectionManager)
      editor.addEditorMouseListener(mouseEventBridge, connection)
      editor.addEditorMouseMotionListener(mouseEventBridge, connection)
      val iconVisibilityController = IconVisibilityController()
      editor.addEditorMouseListener(iconVisibilityController, connection)
      editor.addEditorMouseMotionListener(iconVisibilityController, connection)
      editor.scrollingModel.addVisibleAreaListener(iconVisibilityController, connection)
      Disposer.register(connection, iconVisibilityController)
      editor.scrollingModel.addVisibleAreaListener(MyVisibleAreaListener(editor), connection)
      (editor as EditorEx).foldingModel.addListener(MyFoldingListener(), connection)
      Disposer.register(connection) { DocRenderer.clearCachedLoadingPane(editor) }
      EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
        override fun editorReleased(event: EditorFactoryEvent) {
          if (event.editor == editor) {
            // this ensures renderers are not kept for the released editors
            removeAllItems(editor)
          }
        }
      }, connection)
    }
  }

  fun areListenersAttached(editor: Editor): Boolean = editor.getUserData(LISTENERS_DISPOSABLE) != null

  private class MyVisibleAreaListener(editor: Editor) : VisibleAreaListener {
    private var lastWidth: Int
    private var lastFrcTransform: AffineTransform

    init {
      lastWidth = DocRenderer.calcWidth(editor)
      lastFrcTransform = getTransform(editor)
    }

    override fun visibleAreaChanged(e: VisibleAreaEvent) {
      if (e.newRectangle.isEmpty) return  // ignore switching between tabs
      val editor = e.editor
      val newWidth = DocRenderer.calcWidth(editor)
      val transform = getTransform(editor)
      if (newWidth != lastWidth || transform != lastFrcTransform) {
        lastWidth = newWidth
        lastFrcTransform = transform
        DocRenderItemUpdater.updateRenderers(editor, false)
      }
    }

    companion object {
      private fun getTransform(editor: Editor): AffineTransform {
        return FontInfo.getFontRenderContext(editor.contentComponent).transform
      }
    }
  }

  private class MyFoldingListener : FoldingListener {
    override fun beforeFoldRegionDisposed(region: FoldRegion) {
      if (region is CustomFoldRegion) {
        val renderer = region.renderer
        if (renderer is DocRenderer) {
          renderer.dispose()
        }
      }
    }
  }

  private class IconVisibilityController : EditorMouseListener, EditorMouseMotionListener, VisibleAreaListener, Disposable {
    private var myCurrentItem: DocRenderItem? = null
    private var myQueuedEditor: EditorImpl? = null
    override fun mouseMoved(e: EditorMouseEvent) {
      doUpdate(e.editor, e)
    }

    override fun mouseExited(e: EditorMouseEvent) {
      doUpdate(e.editor, e)
    }

    override fun visibleAreaChanged(e: VisibleAreaEvent) {
      val editor = e.editor as EditorImpl
      if (editor.isCursorHidden) return
      if (myQueuedEditor == null) {
        myQueuedEditor = editor
        // delay update: multiple visible area updates within same EDT event will cause only one icon update,
        // and we'll not observe the item in inconsistent state during toggling
        SwingUtilities.invokeLater {
          if (myQueuedEditor != null && myQueuedEditor?.isDisposed == false) {
            myQueuedEditor?.let { doUpdate(it, null) }
          }
          myQueuedEditor = null
        }
      }
    }

    private fun doUpdate(editor: Editor, event: EditorMouseEvent?) {
      var y = 0
      var offset = -1
      if (event == null) {
        val info = MouseInfo.getPointerInfo()
        if (info != null) {
          val screenPoint = info.location
          val component = editor.component
          val componentPoint = Point(screenPoint)
          SwingUtilities.convertPointFromScreen(componentPoint, component)
          if (Rectangle(component.size).contains(componentPoint)) {
            val editorPoint = Point(screenPoint)
            SwingUtilities.convertPointFromScreen(editorPoint, editor.contentComponent)
            y = editorPoint.y
            offset = editor.visualPositionToOffset(VisualPosition(editor.yToVisualLine(y), 0))
          }
        }
      }
      else {
        y = event.mouseEvent.y
        offset = event.offset
      }
      val item = if (offset < 0) null else findItem(editor, y, offset)
      if (item !== myCurrentItem) {
        if (myCurrentItem != null) myCurrentItem!!.setIconVisible(false)
        myCurrentItem = item
        if (myCurrentItem != null) myCurrentItem!!.setIconVisible(true)
      }
    }

    override fun dispose() {
      myCurrentItem = null
      myQueuedEditor = null
    }

    companion object {
      private fun findItem(editor: Editor, y: Int, neighborOffset: Int): DocRenderItem? {
        val document = editor.document
        val lineNumber = document.getLineNumber(neighborOffset)
        val searchStartOffset = document.getLineStartOffset(0.coerceAtLeast(lineNumber - 1))
        val searchEndOffset = document.getLineEndOffset(lineNumber)
        val items = getInstance().getItems(editor) ?: return null
        for (item in items) {
          val highlighter = item.highlighter
          if (highlighter.isValid && highlighter.startOffset <= searchEndOffset && highlighter.endOffset >= searchStartOffset) {
            var itemStartY = 0
            var itemEndY = 0
            if (item.foldRegion == null) {
              itemStartY = editor.visualLineToY(editor.offsetToVisualLine(highlighter.startOffset, false))
              itemEndY = editor.visualLineToY(editor.offsetToVisualLine(highlighter.endOffset, true)) + editor.lineHeight
            }
            else {
              val cfr = item.foldRegion
              val location = cfr!!.location
              if (location != null) {
                itemStartY = location.y
                itemEndY = itemStartY + cfr.heightInPixels
              }
            }
            if (y in itemStartY until itemEndY) return item
            break
          }
        }
        return null
      }
    }
  }
}