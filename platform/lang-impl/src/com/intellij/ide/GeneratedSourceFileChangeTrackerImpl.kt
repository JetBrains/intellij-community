// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)
@file:Suppress("SSBasedInspection")

package com.intellij.ide

import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.takeWhile
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Internal
class GeneratedSourceFileChangeTrackerImpl(
  private val project: Project,
  private val coroutineScope: CoroutineScope
) : GeneratedSourceFileChangeTracker() {
  private val filesToCheck: MutableSet<VirtualFile> = Collections.synchronizedSet(HashSet())
  private val editedGeneratedFiles: MutableSet<VirtualFile> = Collections.synchronizedSet(HashSet())

  private val checkRequests = MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val checkRequestsJob = AtomicReference<Job>()

  init {
    startHandlingCheckRequests()
  }

  private fun startHandlingCheckRequests() {
    checkRequestsJob.getAndSet(coroutineScope.launch {
      checkRequests
        .takeWhile { it }
        .debounce(500)
        .collectLatest {
          checkFiles()
        }
    })?.cancel()
  }

  companion object {
    // static non-final by design
    @JvmField
    var IN_TRACKER_TEST: Boolean = false

    private fun isListenerInactive(): Boolean = !IN_TRACKER_TEST && ApplicationManager.getApplication().isUnitTestMode
  }

  @TestOnly
  fun waitForAlarm(timeout: Long, timeUnit: TimeUnit) {
    check(!ApplicationManager.getApplication().isWriteAccessAllowed) { "Must not wait for the alarm under write action" }
    try {
      val job = checkRequestsJob.getAndSet(null) ?: return
      runBlocking {
        checkRequests.emit(false)
        withTimeout(timeUnit.toMillis(timeout)) {
          job.join()
        }
      }
    }
    finally {
      startHandlingCheckRequests()
    }
  }

  @TestOnly
  @Throws(Exception::class)
  fun cancelAllAndWait(timeout: Long, timeUnit: TimeUnit) {
    filesToCheck.clear()
    runBlocking {
      try {
        withTimeout(timeUnit.toMillis(timeout)) {
          checkRequestsJob.get()?.cancelAndJoin()
        }
      }
      finally {
        startHandlingCheckRequests()
      }
    }
  }

  override fun isEditedGeneratedFile(file: VirtualFile): Boolean = editedGeneratedFiles.contains(file)

  internal class MyDocumentListener : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      if (isListenerInactive()) {
        return
      }

      val openProjects = ProjectUtilCore.getOpenProjects()
      if (openProjects.isEmpty()) {
        return
      }

      val file = FileDocumentManager.getInstance().getFile(event.document)
      if (file == null || LightVirtualFile.shouldSkipEventSystem(file)) {
        return
      }

      for (project in openProjects) {
        if (project.isDisposed) {
          continue
        }

        // It's too costly to synchronously check whether the file is under the project, so delegate the check to
        // the background activities of all projects
        val fileChangeTracker = getInstance(project) as GeneratedSourceFileChangeTrackerImpl
        fileChangeTracker.filesToCheck.add(file)
        check(fileChangeTracker.checkRequests.tryEmit(true))
      }
    }
  }

  internal class MyProjectManagerListener : ProjectManagerListener {
    @Suppress("removal", "OVERRIDE_DEPRECATION")
    override fun projectOpened(project: Project) {
      if (isListenerInactive()) {
        return
      }

      (getInstance(project) as GeneratedSourceFileChangeTrackerImpl).projectOpened()
    }
  }

  private fun projectOpened() {
    val connection = project.messageBus.connect(coroutineScope)
    connection.subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
      override fun fileContentReloaded(file: VirtualFile, document: Document) {
        filesToCheck.remove(file)
        if (editedGeneratedFiles.remove(file)) {
          EditorNotifications.getInstance(project).updateNotifications(file)
        }
      }
    })
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        editedGeneratedFiles.remove(file)
      }
    })
    connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        resetOnRootsChanged()
      }
    })
    connection.subscribe(AdditionalLibraryRootsListener.TOPIC,
                         AdditionalLibraryRootsListener { _: String?, _: Collection<VirtualFile?>?, _: Collection<VirtualFile?>?, _: String? -> resetOnRootsChanged() })
  }

  private fun resetOnRootsChanged() {
    filesToCheck.addAll(editedGeneratedFiles)
    editedGeneratedFiles.clear()
    check(checkRequests.tryEmit(true))
  }

  private suspend fun checkFiles() {
    val files = synchronized(filesToCheck) {
      filesToCheck.toList().also {
        filesToCheck.clear()
      }
    }
    if (files.isEmpty()) {
      return
    }

    val counter = AtomicInteger()

    val fileIndex = project.serviceAsync<ProjectFileIndex>()
    readAction {
      val newEditedGeneratedFiles = ArrayList<VirtualFile>()
      try {
        while (counter.get() < files.size) {
          ProgressManager.checkCanceled()

          val file = files[counter.get()]
          if (fileIndex.isInContent(file) || fileIndex.isInLibrary(file)) {
            if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project)) {
              newEditedGeneratedFiles.add(file)
            }
          }

          counter.incrementAndGet()
        }
      }
      finally {
        // In case of canceled exception or (really) any other exception,
        // some files could be already processed let's not wait for the next available read lock slot
        if (!newEditedGeneratedFiles.isEmpty()) {
          editedGeneratedFiles.addAll(newEditedGeneratedFiles)
          EditorNotifications.getInstance(project).updateAllNotifications()
        }
      }
    }
  }
}
