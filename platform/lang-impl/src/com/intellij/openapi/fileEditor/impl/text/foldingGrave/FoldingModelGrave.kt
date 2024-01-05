// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text.foldingGrave

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCache
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
@Service(Level.PROJECT)
internal class FoldingModelGrave(private val project: Project, private val scope: CoroutineScope) : TextEditorCache<FoldingState>(project, scope) {
  override fun namePrefix() = "persistent-folding"
  override fun valueExternalizer() = FoldingState.FoldingStateExternalizer
  override fun useHeapCache() = true

  private val fileToModel: MutableMap<Int, FoldingModelEx> = ConcurrentHashMap()

  companion object {
    private val logger = Logger.getInstance(FoldingModelGrave::class.java)
  }

  fun getFoldingState(file: VirtualFile?): FoldingState? {
    if (!isEnabled() || file !is VirtualFileWithId) {
      return null
    }
    return cache[file.id]
  }

  fun setFoldingModel(file: VirtualFile?, foldingModel: FoldingModelEx) {
    if (!isEnabled() || file !is VirtualFileWithId) {
      return
    }
    fileToModel[file.id] = foldingModel
  }

  fun subscribeFileClosed() {
    project.getMessageBus().connect().subscribe<FileEditorManagerListener.Before>(
      FileEditorManagerListener.Before.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener.Before {
        override fun beforeFileClosed(source: FileEditorManager, file: VirtualFile) = persistFoldingState(source, file)
      }
    )
  }

  private fun persistFoldingState(editorManager: FileEditorManager, file: VirtualFile) {
    if (!isEnabled() || file !is VirtualFileWithId) {
      return
    }
    val fileEditor = editorManager.getSelectedEditor(file)
    if (fileEditor !is TextEditor) {
      return
    }
    if (fileEditor.getEditor().getEditorKind() != EditorKind.MAIN_EDITOR) {
      return
    }
    val model = fileToModel.remove(file.id)
    if (model == null) {
      return
    }
    val document = FileDocumentManager.getInstance().getCachedDocument(file)
    if (document == null) {
      return
    }
    val foldRegions = model.allFoldRegions
    if (foldRegions.isEmpty()) {
      return
    }
    val foldingState = FoldingState.create(document.contentHash(), foldRegions)
    scope.launch(Dispatchers.IO) {
      cache[file.id] = foldingState
      logger.debug { "stored folding state ${foldingState} for $file" }
    }
  }

  private fun isEnabled(): Boolean {
    return Registry.`is`("cache.folding.model.on.disk")
  }
}
