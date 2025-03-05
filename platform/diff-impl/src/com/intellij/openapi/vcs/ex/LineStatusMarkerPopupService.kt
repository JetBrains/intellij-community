// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.codeInsight.hint.EditorHintListener
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextComponent
import com.intellij.ui.HintHint
import com.intellij.ui.HintListener
import com.intellij.ui.LightweightHint
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter

@Service(Service.Level.APP)
class LineStatusMarkerPopupService {
  private var activePopupDisposable: CheckedDisposable? = null

  @RequiresEdt
  fun buildAndShowPopup(parentDisposable: Disposable, editor: Editor, mousePosition: Point?, builder: (popupDisposable: Disposable) -> LineStatusMarkerPopupPanel) {
    val popupDisposable = getNextPopupDisposable(parentDisposable)
    val markerPopupPanel = builder(popupDisposable)
    showPopupAt(editor, markerPopupPanel, mousePosition, popupDisposable)
  }

  private fun getNextPopupDisposable(parentDisposable: Disposable): CheckedDisposable {
    closeActivePopup()
    activePopupDisposable = Disposer.newCheckedDisposable(parentDisposable, "LineStatusMarkerPopup")

    return activePopupDisposable!!
  }

  fun closeActivePopup() {
    if (activePopupDisposable != null) Disposer.dispose(activePopupDisposable!!)
  }

  @RequiresEdt
  private fun showPopupAt(
    editor: Editor,
    panel: LineStatusMarkerPopupPanel,
    mousePosition: Point?,
    popupDisposable: CheckedDisposable,
  ) {
    if (popupDisposable.isDisposed) return

    val hint = LightweightHint(panel)
    Disposer.register(popupDisposable, Disposable {
      UIUtil.invokeLaterIfNeeded {
        hint.hide()
      }
    })
    hint.addHintListener(HintListener { Disposer.dispose(popupDisposable) }) // doesn't need it anymore?
    hint.setForceLightweightPopup(true)

    // if there are no listeners, events are passed to the top level
    panel.addMouseListener(object : MouseAdapter() {})

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
    setupEditorListeners(editor, popupDisposable)
    showHint(hint, editor, point)

    if (!hint.isVisible()) {
      Disposer.dispose(popupDisposable)
    }
  }

  private fun showHint(hint: LightweightHint, editor: Editor, point: Point) {
    HintManagerImpl.doShowInGivenLocation(hint, editor, point, HintHint(editor, point), true)
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

    private fun setupEditorListeners(editor: Editor, popupDisposable: Disposable) {
      trackFileEditorChange(popupDisposable)
      trackDocumentChange(editor, popupDisposable)
      trackSelection(editor, popupDisposable)
      trackCaretPosition(editor, popupDisposable)
      trackScrolling(editor, popupDisposable)
      trackMouseClick(editor, popupDisposable)
      trackEditorLookup(editor, popupDisposable)
      trackLafChanging(popupDisposable)
    }

    private fun trackFileEditorChange(popupDisposable: Disposable) {
      ApplicationManager.getApplication().getMessageBus().connect(popupDisposable)
        .subscribe<FileEditorManagerListener>(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
          override fun selectionChanged(event: FileEditorManagerEvent) {
            closeActivePopup()
          }
        })
    }

    private fun trackDocumentChange(editor: Editor, popupDisposable: Disposable) {
      editor.getDocument().addDocumentListener(object : BulkAwareDocumentListener {
        override fun documentChangedNonBulk(event: DocumentEvent) {
          if (event.getOldLength() != 0 || event.getNewLength() != 0) onDocumentChange()
        }

        override fun bulkUpdateFinished(document: Document) {
          onDocumentChange()
        }

        fun onDocumentChange() {
          closeActivePopup()
        }
      }, popupDisposable)
    }

    private fun trackSelection(editor: Editor, popupDisposable: Disposable) {
      editor.getSelectionModel().addSelectionListener(object : SelectionListener {
        override fun selectionChanged(e: SelectionEvent) {
          closeActivePopup()
        }
      }, popupDisposable)
    }

    private fun trackCaretPosition(editor: Editor, popupDisposable: Disposable) {
      editor.getCaretModel().addCaretListener(object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
          closeActivePopup()
        }
      }, popupDisposable)
    }

    private fun trackScrolling(editor: Editor, popupDisposable: Disposable) {
      editor.getScrollingModel().addVisibleAreaListener(object : VisibleAreaListener {
        override fun visibleAreaChanged(e: VisibleAreaEvent) {
          val old = e.oldRectangle
          val new = e.newRectangle
          if (old != null && old.x == new.x && old.y == new.y) return

          closeActivePopup()
        }
      }, popupDisposable)
    }

    private fun trackMouseClick(editor: Editor, popupDisposable: Disposable) {
      editor.addEditorMouseListener(object : EditorMouseListener {
        override fun mousePressed(event: EditorMouseEvent) {
          closeActivePopup()
        }
      }, popupDisposable)
    }

    private fun trackEditorLookup(editor: Editor, popupDisposable: Disposable) {
      val project = editor.project
      if (project != null) LookupManager.hideActiveLookup(project) // reset current

      ApplicationManager.getApplication().getMessageBus().connect(popupDisposable)
        .subscribe(EditorHintListener.TOPIC, object : EditorHintListener {
          override fun hintShown(sourceEditor: Editor, editorHint: LightweightHint, flags: Int, hintInfo: HintHint) {
            if (sourceEditor !== editor) return
            if (editorHint is Lookup) {
              closeActivePopup()
            }
          }
        })
    }

    private fun trackLafChanging(popupDisposable: Disposable) {
      ApplicationManager.getApplication().messageBus.connect(popupDisposable)
        .subscribe(LafManagerListener.TOPIC, LafManagerListener {
          closeActivePopup()
        })
    }

    @JvmStatic
    val instance: LineStatusMarkerPopupService
      get() = ApplicationManager.getApplication()
        .getService<LineStatusMarkerPopupService>(LineStatusMarkerPopupService::class.java)
  }
}

private fun closeActivePopup() {
  LineStatusMarkerPopupService.instance.closeActivePopup()
}

private class LineStatusMakerEscEditorHandler(private val delegate: EditorActionHandler) : EditorActionHandler() {
  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return delegate.isEnabled(editor, caret, dataContext)
  }

  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    closeActivePopup()
    delegate.execute(editor, caret, dataContext)
  }
}