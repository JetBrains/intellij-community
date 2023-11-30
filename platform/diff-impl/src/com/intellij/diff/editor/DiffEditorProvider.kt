// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.diff.tools.combined.*
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.ex.StructureViewFileEditorProvider
import com.intellij.openapi.fileEditor.impl.DefaultPlatformFileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls

internal class DiffEditorProvider : DefaultPlatformFileEditorProvider, StructureViewFileEditorProvider, DumbAware {
  companion object {
    @NonNls
    const val DIFF_EDITOR_PROVIDER_ID = "DiffEditor"
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is CombinedDiffVirtualFile || file is DiffVirtualFile
  }

  override fun acceptRequiresReadAction() = false

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    if (file is CombinedDiffVirtualFile) {
      val sourceId = file.sourceId
      val modelRepository = project.service<CombinedDiffModelRepository>()
      var combinedDiffModel = modelRepository.findModel(sourceId)
      if (combinedDiffModel == null && file is CombinedDiffModelBuilder) {
        combinedDiffModel = file.createModel(sourceId)
        modelRepository.registerModel(sourceId, combinedDiffModel)
      }
      requireNotNull(combinedDiffModel) { "Combined diff model doesn't registered for $sourceId" }
      return CombinedDiffEditor(file, project.service<CombinedDiffComponentFactoryProvider>().create(combinedDiffModel))
    }

    val processor = (file as DiffVirtualFile).createProcessor(project)
    return DiffRequestProcessorEditor(file, processor)
  }

  override fun disposeEditor(editor: FileEditor) {
    Disposer.dispose(editor)
  }

  override fun getEditorTypeId(): String = DIFF_EDITOR_PROVIDER_ID

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE

  override fun getStructureViewBuilder(project: Project, file: VirtualFile): StructureViewBuilder? = null
}
