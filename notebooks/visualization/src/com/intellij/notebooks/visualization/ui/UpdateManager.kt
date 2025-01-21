// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.InlaysChangedListener
import com.intellij.notebooks.visualization.UpdateContext
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Key

class UpdateManager(val editor: EditorImpl) : Disposable {

  private var updateCtx: UpdateContext? = null

  /*
   EditorImpl sets `myDocumentChangeInProgress` attribute to true during document update processing, that prevents correct update
   of custom folding regions.When this flag is set, folding updates will be postponed until the editor finishes its work.
   */
  private var editorIsProcessingDocument = false

  private var postponedUpdates = mutableListOf<UpdateContext>()

  /**
   * Listens for inlay changes (called after all inlays are updated). Feel free to convert it to the EP if you need another listener
   */
  var changedListener: InlaysChangedListener? = null

  init {
    editor.document.addDocumentListener(object : BulkAwareDocumentListener.Simple {
      override fun beforeDocumentChange(document: Document) {
        editorIsProcessingDocument = true
      }

      override fun afterDocumentChange(document: Document) {
        editorIsProcessingDocument = false
        postponedUpdates.forEach {
          it.applyUpdates(editor)
        }
        postponedUpdates.clear()
        finalizeChanges()
      }
    }, this)
    UPDATE_MANAGER_KEY.set(editor, this)
  }

  fun <T> update(force: Boolean = false, block: (updateCtx: UpdateContext) -> T): T {
    val ctx = updateCtx
    return if (ctx != null) {
      block(ctx)
    }
    else {
      val newCtx = UpdateContext(force)
      updateCtx = newCtx
      try {
        val jupyterBoundsChangeHandler = JupyterBoundsChangeHandler.get(editor)
        jupyterBoundsChangeHandler.postponeUpdates()
        val r = keepScrollingPositionWhile(editor) {
          val r = block(newCtx)
          updateCtx = null
          if (editorIsProcessingDocument) {
            postponedUpdates.add(newCtx)
          }
          else {
            newCtx.applyUpdates(editor)
            finalizeChanges()
          }
          r
        }
        r
      }
      finally {
        updateCtx = null
      }
    }
  }

  private fun finalizeChanges() {
    inlaysChanged()
    val jupyterBoundsChangeHandler = JupyterBoundsChangeHandler.get(editor)
    jupyterBoundsChangeHandler.boundsChanged()
    jupyterBoundsChangeHandler.performPostponed()
  }

  private fun inlaysChanged() {
    changedListener?.inlaysChanged()
  }

  override fun dispose() {

  }
}

private val UPDATE_MANAGER_KEY = Key<UpdateManager>("UPDATE_MANAGER_KEY")

val Editor.updateManager
  get() = UPDATE_MANAGER_KEY.get(this)