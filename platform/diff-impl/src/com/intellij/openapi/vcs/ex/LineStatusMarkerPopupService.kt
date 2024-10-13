// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

@Service(Service.Level.APP)
internal class LineStatusMarkerPopupService {
  private var lastKnownHint: Hint? = null

  @RequiresEdt
  fun showPopupAt(
    editor: Editor,
    panel: LineStatusMarkerPopupPanel,
    mousePosition: Point?,
    popupDisposable: Disposable,
  ) {
    val hint = LightweightHint(panel)
    Disposer.register(popupDisposable, Disposable {
      UIUtil.invokeLaterIfNeeded {
        hint.hide()
        resetLastHint()
      }
    })
    hint.addHintListener(HintListener { Disposer.dispose(popupDisposable) })
    hint.setForceLightweightPopup(true)


    val line = editor.getCaretModel().logicalPosition.line
    val point = HintManagerImpl.getHintPosition(hint, editor, LogicalPosition(line, 0), HintManager.UNDER)
    if (mousePosition != null) { // show right after the nearest line
      val lineHeight = editor.getLineHeight()
      var delta = (point.y - mousePosition.y) % lineHeight
      if (delta < 0) delta += lineHeight
      point.y = mousePosition.y + delta
    }
    point.x -= panel.editorTextOffset // align a main editor with the one in popup

    trackInnerEditorResizing(panel, hint, popupDisposable)
    setupEditorListeners(editor, popupDisposable, hint)

    beforeShowNewHint(hint)
    showHint(hint, editor, point)

    if (!hint.isVisible()) {
      Disposer.dispose(popupDisposable)
    }
  }

  private fun beforeShowNewHint(newHint: LightweightHint) {
    if (lastKnownHint != null) {
      lastKnownHint!!.hide()
      resetLastHint()
    }
    lastKnownHint = newHint
  }

  private fun resetLastHint() {
    lastKnownHint = null
  }

  private fun showHint(hint: LightweightHint, editor: Editor, point: Point) {
    val parent = editor.getComponent().rootPane
    val hintHint = HintHint(editor, point)
    hint.show(parent, point.x, point.y, editor.getComponent(), hintHint)
  }

  companion object {
    private fun trackInnerEditorResizing(
      panel: LineStatusMarkerPopupPanel,
      hint: LightweightHint,
      popupDisposable: Disposable,
    ) {
      val adapter: ComponentAdapter = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          hint.pack()
        }
      }

      val componentsWithListener = mutableListOf<EditorTextComponent>()

      UIUtil.forEachComponentInHierarchy(panel) { c ->
        if (c is EditorTextComponent) {
          componentsWithListener.add(c)
          c.component.addComponentListener(adapter)
        }
      }

      Disposer.register(popupDisposable, Disposable {
        for (c in componentsWithListener) {
          c.component.removeComponentListener(adapter)
        }
      })
    }

    private fun setupEditorListeners(editor: Editor, popupDisposable: Disposable, hint: LightweightHint) {
      trackFileEditorChange(popupDisposable, hint)
      trackDocumentChange(editor, popupDisposable, hint)
      trackSelection(editor, popupDisposable, hint)
      trackCaretPosition(editor, popupDisposable, hint)
      trackScrolling(editor, popupDisposable, hint)
      trackMouseClick(editor, popupDisposable, hint)
    }

    private fun trackFileEditorChange(popupDisposable: Disposable, hint: LightweightHint) {
      ApplicationManager.getApplication().getMessageBus().connect(popupDisposable)
        .subscribe<FileEditorManagerListener>(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
          override fun selectionChanged(event: FileEditorManagerEvent) {
            hint.hide()
          }
        })
    }

    private fun trackDocumentChange(editor: Editor, popupDisposable: Disposable, hint: LightweightHint) {
      editor.getDocument().addDocumentListener(object : BulkAwareDocumentListener {
        override fun documentChangedNonBulk(event: DocumentEvent) {
          if (event.getOldLength() != 0 || event.getNewLength() != 0) onDocumentChange()
        }

        override fun bulkUpdateFinished(document: Document) {
          onDocumentChange()
        }

        fun onDocumentChange() {
          hint.hide()
        }
      }, popupDisposable)
    }

    private fun trackSelection(editor: Editor, popupDisposable: Disposable, hint: LightweightHint) {
      editor.getSelectionModel().addSelectionListener(object : SelectionListener {
        override fun selectionChanged(e: SelectionEvent) {
          hint.hide()
        }
      }, popupDisposable)
    }

    private fun trackCaretPosition(editor: Editor, popupDisposable: Disposable, hint: LightweightHint) {
      editor.getCaretModel().addCaretListener(object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
          hint.hide()
        }
      }, popupDisposable)
    }

    private fun trackScrolling(editor: Editor, popupDisposable: Disposable, hint: LightweightHint) {
      editor.getScrollingModel().addVisibleAreaListener(object : VisibleAreaListener {
        override fun visibleAreaChanged(e: VisibleAreaEvent) {
          hint.hide()
        }
      }, popupDisposable)
    }

    private fun trackMouseClick(editor: Editor, popupDisposable: Disposable, hint: LightweightHint) {
      editor.addEditorMouseListener(object : EditorMouseListener {
        override fun mousePressed(event: EditorMouseEvent) {
          hint.hide()
        }
      }, popupDisposable)
    }

    @JvmStatic
    val instance: LineStatusMarkerPopupService
      get() = ApplicationManager.getApplication()
        .getService<LineStatusMarkerPopupService>(LineStatusMarkerPopupService::class.java)
  }
}
