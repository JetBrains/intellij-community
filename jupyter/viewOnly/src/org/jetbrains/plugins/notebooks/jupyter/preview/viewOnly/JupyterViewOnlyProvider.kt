// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.notebooks.jupyter.preview.viewOnly

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.notebooks.jupyter.JupyterFileType


class JupyterViewOnlyProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = FileTypeRegistry.getInstance().isFileOfType(file, JupyterFileType)

  override fun createEditor(project: Project, file: VirtualFile): FileEditor = JupyterViewOnlyFileEditor.create(file)

  override fun getEditorTypeId(): String = "jupyter-view-only-provider"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
