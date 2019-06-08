/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.editor

import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

class DiffEditorProvider : AsyncFileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is DiffVirtualFile
  }

  override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
    val builder = (file as DiffVirtualFile).createProcessorAsync(project)
    return object : AsyncFileEditorProvider.Builder() {
      override fun build() = DiffRequestProcessorEditor(file, builder.build())
    }
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val builder = (file as DiffVirtualFile).createProcessorAsync(project)
    return DiffRequestProcessorEditor(file, builder.build())
  }

  override fun disposeEditor(editor: FileEditor) {
    Disposer.dispose(editor)
  }

  override fun getEditorTypeId(): String = "DiffEditor"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
