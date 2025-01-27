// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration.Companion.minutes

internal class WolfListeners(
  project: Project,
  private val wolfTheProblemSolver: WolfTheProblemSolverImpl,
  coroutineScope: CoroutineScope,
) {
  // VirtualFile or Runnable to implement waitForFilesQueuedForInvalidationAreProcessed
  private val invalidateFileRequests = MutableSharedFlow<Any>(
    extraBufferCapacity = Int.MAX_VALUE,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  init {
    coroutineScope.launch {
      invalidateFileRequests
        .collect { file ->
          if (file is kotlinx.coroutines.Runnable) {
            file.run()
            return@collect
          }

          file as VirtualFile

          val toRemove = readAction {
            !file.isValid || !wolfTheProblemSolver.isToBeHighlighted(file)
          }
          if (toRemove) {
            wolfTheProblemSolver.doRemove(file)
          }
        }
    }

    PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
      override fun childAdded(event: PsiTreeChangeEvent) {
        childrenChanged(event)
      }

      override fun childRemoved(event: PsiTreeChangeEvent) {
        childrenChanged(event)
      }

      override fun childReplaced(event: PsiTreeChangeEvent) {
        childrenChanged(event)
      }

      override fun childMoved(event: PsiTreeChangeEvent) {
        childrenChanged(event)
      }

      override fun propertyChanged(event: PsiTreeChangeEvent) {
        childrenChanged(event)
      }

      override fun childrenChanged(event: PsiTreeChangeEvent) {
        wolfTheProblemSolver.clearSyntaxErrorFlag(event)
      }
    }, wolfTheProblemSolver)
    val busConnection = project.messageBus.connect(coroutineScope)
    busConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        var dirChanged = false
        val toRemove: MutableSet<VirtualFile?> = HashSet()
        for (event in events) {
          if (event is VFileDeleteEvent || event is VFileMoveEvent) {
            val file = event.file
            if (file!!.isDirectory) {
              dirChanged = true
            }
            else {
              toRemove.add(file)
            }
          }
        }
        if (dirChanged) {
          clearInvalidFiles()
        }
        for (file in toRemove) {
          wolfTheProblemSolver.doRemove(file!!)
        }
      }
    })
    busConnection.subscribe(FileStatusListener.TOPIC, object : FileStatusListener {
      override fun fileStatusesChanged() {
        clearInvalidFiles()
      }

      override fun fileStatusChanged(virtualFile: VirtualFile) {
        fileStatusesChanged()
      }
    })

    busConnection.subscribe(DynamicPluginListener.Companion.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        // Ensure we don't have any leftover problems referring to classes from plugin being unloaded
        val allFiles = HashSet<VirtualFile>()
        wolfTheProblemSolver.consumeProblemFiles(allFiles::add)
        wolfTheProblemSolver.consumeProblemFilesFromExternalSources(allFiles::add)
        for (file in allFiles) {
          wolfTheProblemSolver.doRemove(file)
        }
      }
    })
  }

  private fun clearInvalidFiles() {
    wolfTheProblemSolver.consumeProblemFiles { file ->
      invalidateFileRequests.tryEmit(file)
    }
  }

  @TestOnly
  fun waitForFilesQueuedForInvalidationAreProcessed() {
    @Suppress("SSBasedInspection")
    (runBlocking {
      withTimeout(1.minutes) {
        val job = CompletableDeferred<Unit>()
        invalidateFileRequests.emit(Runnable { job.complete(Unit) })
        job.join()
      }
    })
  }
}