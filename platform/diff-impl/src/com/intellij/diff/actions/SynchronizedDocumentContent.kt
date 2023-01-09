// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions

import com.intellij.diff.contents.DiffContentBase
import com.intellij.diff.contents.DocumentContent
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory

/**
 * Diff content that shows a fake document for the diff and synchronizes changes with the real document.
 */
abstract class SynchronizedDocumentContent(
  protected val original: DocumentContent
) : DiffContentBase(), DocumentContent {
  abstract val synchronizer: DocumentsSynchronizer

  private var assignments = 0
  
  protected val fakeDocument = EditorFactory.getInstance().createDocument("").apply {
    putUserData(UndoManager.ORIGINAL_DOCUMENT, original.document)
  }

  override fun getDocument(): Document = fakeDocument

  override fun onAssigned(isAssigned: Boolean) {
    if (isAssigned) {
      if (assignments == 0) synchronizer.startListen()
      assignments++
    }
    else {
      assignments--
      if (assignments == 0) synchronizer.stopListen()
    }
    assert(assignments >= 0)
  }
}