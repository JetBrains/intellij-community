// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo

import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.editor.Editor
import junit.framework.Assert

open class MultiUserEditorUndoWithResetHistoryTest : MultiUserEditorUndoTest() {

  protected open val changesCount: Int get() = 3

  override fun undoFirstEditor() {
    doRandomChangesAndReset()
    super.undoFirstEditor()
  }

  override fun redoFirstEditor() {
    doRandomChangesAndReset()
    super.redoFirstEditor()
  }

  private fun doRandomChangesAndReset() {
    doRandomChangesAndResetHistory(changesCount)
  }

  private fun doRandomChangesAndResetHistory(count: Int) {
    val undoManager = UndoManager.getInstance(myProject) as UndoManagerImpl
    val editor: Editor = getFirstEditor()
    val token = undoManager.createResetUndoHistoryToken(UndoTestCase.getFileEditor(editor))
    Assert.assertNotNull(token)
    val selectionModel = editor.selectionModel
    val start = selectionModel.selectionStart
    val end = selectionModel.selectionEnd

    selectionModel.setSelection(0, 0)

    for (i in 0 until count) {
      typeWithFlush('b')
    }
    for (i in 0 until count) {
      backspace(editor)
    }

    selectionModel.setSelection(start, end)

    val success = token!!.resetHistory()
    Assert.assertTrue("Can't reset history", success)
  }
}


class MultiUserEditorUndoWithResetHistoryAfterManyChangesTest : MultiUserEditorUndoWithResetHistoryTest() {

  override val changesCount: Int
    get() =  UndoManagerImpl.getDocumentUndoLimit() * 2
}