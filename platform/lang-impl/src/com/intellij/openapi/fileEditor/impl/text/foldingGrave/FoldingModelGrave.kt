// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text.foldingGrave

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

internal interface FoldingModelGrave {
  fun getFoldingState(file: VirtualFile?): FoldingState?
  fun setFoldingModel(file: VirtualFile?, foldingModel: FoldingModelEx)
  fun subscribeFileClosed()

  companion object {
    private val logger = Logger.getInstance(FoldingModelGrave::class.java)

    fun getInstance(project: Project): FoldingModelGrave {
      return if (Registry.`is`("cache.folding.model.on.disk")) project.service<FoldingModelGraveImpl>() else EMPTY_GRAVE
    }

    private val EMPTY_GRAVE = object : FoldingModelGrave {
      override fun getFoldingState(file: VirtualFile?) = null
      override fun setFoldingModel(file: VirtualFile?, foldingModel: FoldingModelEx) = Unit
      override fun subscribeFileClosed() = Unit
      override fun toString() = "EMPTY_FOLDING_GRAVE"
    }
  }

  @Service(Service.Level.PROJECT)
  class FoldingModelGraveImpl(private val project: Project, scope: CoroutineScope) : FoldingModelGrave {
    private val fileToState: FoldingModelStore = FoldingModelStore.create(project, scope)
    private val fileToModel: MutableMap<Int, FoldingModelEx> = ConcurrentHashMap()

    override fun getFoldingState(file: VirtualFile?): FoldingState? {
      return if (file is VirtualFileWithId) fileToState[file.id] else null
    }

    override fun setFoldingModel(file: VirtualFile?, foldingModel: FoldingModelEx) {
      if (file is VirtualFileWithId) {
        fileToModel[file.id] = foldingModel
      }
    }

    override fun subscribeFileClosed() {
      project.getMessageBus().connect().subscribe<FileEditorManagerListener.Before>(
        FileEditorManagerListener.Before.FILE_EDITOR_MANAGER,
        object : FileEditorManagerListener.Before {
          override fun beforeFileClosed(source: FileEditorManager, file: VirtualFile) = persistFoldingState(file)
        }
      )
    }

    private fun persistFoldingState(file: VirtualFile) {
      if (file is VirtualFileWithId) {
        val model = fileToModel.remove(file.id)
        if (model != null) {
          val document = FileDocumentManager.getInstance().getCachedDocument(file)
          if (document != null) {
            val foldingState = FoldingState.create(document, model.allFoldRegions)
            fileToState[file.id] = foldingState
            logger.debug { "stored folding state ${foldingState} for $file" }
          }
        }
      }
    }
  }
}
