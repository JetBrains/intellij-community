// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.ex.StructureViewFileEditorProvider
import com.intellij.openapi.fileEditor.impl.DefaultPlatformFileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls

@NonNls
private const val DIFF_EDITOR_PROVIDER_ID = "DiffEditor"

internal class DiffFileEditorProvider : DefaultPlatformFileEditorProvider, StructureViewFileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is DiffViewerVirtualFile

  override fun acceptRequiresReadAction() = false

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val processor = (file as DiffViewerVirtualFile).createViewer(project)
    val editor = when (processor) {
      is DiffRequestProcessor -> @Suppress("DEPRECATION") DiffRequestProcessorEditor(file, processor)
      else -> DiffEditorViewerFileEditor(project, file, processor)
    }
    DiffRequestProcessorEditorCustomizer.customize(file, editor, processor.context)
    return editor
  }

  override fun disposeEditor(editor: FileEditor) {
    Disposer.dispose(editor)
  }

  override fun getEditorTypeId(): String = DIFF_EDITOR_PROVIDER_ID

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE

  override fun getStructureViewBuilder(project: Project, file: VirtualFile): StructureViewBuilder? = null
}
