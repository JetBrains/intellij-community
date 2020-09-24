// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.codeInsight

import com.intellij.codeInsight.editorActions.PasteHandler
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.Producer
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

abstract class ImagePasteHandler(originalAction: EditorActionHandler) : PasteHandler(originalAction) {
  abstract val supportedFileType: FileType

  // prevents to be overridden in descendants
  final override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
    super.isEnabledForCaret(editor, caret, dataContext) //

  final override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) =
    when (dataContext?.getData(CommonDataKeys.VIRTUAL_FILE)?.fileType) {
      supportedFileType -> {
        assert(caret == null) { "Invocation of 'paste' operation for specific caret is not supported" }

        // Provides special producer that extracts image from clipboard
        execute(editor, dataContext, Producer<Transferable> {
          CopyPasteManager.getInstance().let { manager ->
            if (manager.areDataFlavorsAvailable(DataFlavor.imageFlavor))
              manager.contents
            else
              null
          }
        })

        executedOnSupportedFile(editor, dataContext)
      }

      else -> super.doExecute(editor, caret, dataContext)
    }

  open fun executedOnSupportedFile(editor: Editor, dataContext: DataContext?) = Unit
}