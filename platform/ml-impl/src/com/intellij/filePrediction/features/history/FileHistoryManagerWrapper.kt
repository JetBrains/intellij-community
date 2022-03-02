// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.SequentialTaskExecutor

@Service(Service.Level.PROJECT)
class FileHistoryManagerWrapper(private val project: Project) : Disposable {
  companion object {
    private const val MAX_NGRAM_SEQUENCE = 3

    fun getInstance(project: Project) = project.service<FileHistoryManagerWrapper>()
    fun getInstanceIfCreated(project: Project) = project.serviceIfCreated<FileHistoryManagerWrapper>()
  }

  private val executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("NextFilePrediction")
  private val lazyManager: Lazy<FileHistoryManager> = lazy { FileHistoryManager(FileHistoryPersistence.loadNGrams(project, MAX_NGRAM_SEQUENCE)) }

  private fun getManagerIfInitialized(): FileHistoryManager? {
    return if (lazyManager.isInitialized()) lazyManager.value else null
  }

  fun calcNGramFeatures(candidates: List<VirtualFile>): FilePredictionNGramFeatures? {
    val managerIfInitialized = getManagerIfInitialized()
    return managerIfInitialized?.calcNGramFeatures(candidates.map { it.url })
  }

  fun calcNextFileProbability(file: VirtualFile): Double {
    return getManagerIfInitialized()?.calcNextFileProbability(file.url) ?: 0.0
  }

  private fun onFileOpened(file: VirtualFile) {
    if (ProjectManagerImpl.isLight(project)) {
      return
    }

    executor.submit {
      BackgroundTaskUtil.runUnderDisposeAwareIndicator(this, Runnable {
        lazyManager.value.onFileOpened(file.url)
      })
    }
  }

  private fun onProjectClosed(project: Project) {
    ApplicationManager.getApplication().executeOnPooledThread {
      getManagerIfInitialized()?.saveFileHistory(project)
    }
  }

  override fun dispose() {
    executor.shutdown()
  }

  internal class ProjectClosureListener : ProjectManagerListener {
    override fun projectClosing(project: Project) {
      getInstanceIfCreated(project)?.onProjectClosed(project)
    }
  }

  internal class EditorManagerListener : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
      val newFile = event.newFile ?: return
      getInstance(event.manager.project).onFileOpened(newFile)
    }
  }
}
