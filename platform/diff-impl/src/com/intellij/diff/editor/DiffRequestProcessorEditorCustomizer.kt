// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile

interface DiffRequestProcessorEditorCustomizer {
  companion object{
    private val EP_DIFF_REQUEST_PROCESSOR_EDITOR_CUSTOMIZER =
      ExtensionPointName<DiffRequestProcessorEditorCustomizer>("com.intellij.diff.editor.diffRequestProcessorEditorCustomizer")

    fun customize(file: VirtualFile, editor: FileEditor, processor: DiffRequestProcessor) {
      EP_DIFF_REQUEST_PROCESSOR_EDITOR_CUSTOMIZER.forEachExtensionSafe { e -> e.customize(file, editor, processor) }
    }
  }

  fun customize(file: VirtualFile, editor: FileEditor, processor: DiffRequestProcessor)
}
