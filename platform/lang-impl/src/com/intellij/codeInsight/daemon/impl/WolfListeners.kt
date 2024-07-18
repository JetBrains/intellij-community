// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ReadAction
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
import com.intellij.util.CommonProcessors.CollectProcessor
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class WolfListeners(private val project: Project, private val wolfTheProblemSolver: WolfTheProblemSolverImpl) {
  private val invalidateFileQueue = MergingUpdateQueue(
    name = "WolfListeners.invalidateFileQueue",
    mergingTimeSpan = 0,
    isActive = true,
    modalityStateComponent = null,
    parent = wolfTheProblemSolver,
    activationComponent = null,
    executeInDispatchThread = false,
  )

  init {
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
        this@WolfListeners.wolfTheProblemSolver.clearSyntaxErrorFlag(event)
      }
    }, wolfTheProblemSolver)
    val busConnection = project.messageBus.simpleConnect()
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
          this@WolfListeners.wolfTheProblemSolver.doRemove(file!!)
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

    busConnection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        // Ensure we don't have any leftover problems referring to classes from plugin being unloaded
        val allFiles: Set<VirtualFile> = HashSet()
        wolfTheProblemSolver.processProblemFiles(CollectProcessor(allFiles))
        wolfTheProblemSolver.processProblemFilesFromExternalSources(CollectProcessor(allFiles))
        for (file in allFiles) {
          this@WolfListeners.wolfTheProblemSolver.doRemove(file)
        }
      }
    })
  }

  private fun clearInvalidFiles() {
    wolfTheProblemSolver.processProblemFiles { file ->
      invalidateFileQueue.queue(Update.create(file) {
        val toRemove = ReadAction.compute<Boolean, RuntimeException> {
          !project.isDisposed && (!file.isValid || !wolfTheProblemSolver.isToBeHighlighted(file))
        }
        if (toRemove) {
          wolfTheProblemSolver.doRemove(file)
        }
      })
      true
    }
  }

  @TestOnly
  fun waitForFilesQueuedForInvalidationAreProcessed() {
    try {
      invalidateFileQueue.waitForAllExecuted(1, TimeUnit.MINUTES)
    }
    catch (e: TimeoutException) {
      throw RuntimeException(e)
    }
  }
}
