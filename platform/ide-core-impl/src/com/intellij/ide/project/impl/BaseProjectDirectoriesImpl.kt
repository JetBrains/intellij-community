// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.project.impl

import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.BaseProjectDirectoriesDiff
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePrefixTreeFactory
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.virtualFile
import com.intellij.workspaceModel.storage.EntityStorageSnapshot
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicInteger

open class BaseProjectDirectoriesImpl(val project: Project, scope: CoroutineScope) : BaseProjectDirectories(project) {

  private val virtualFilesTree = VirtualFilePrefixTreeFactory.createSet()
  private val flow = MutableSharedFlow<VersionedStorageChange>(extraBufferCapacity = 1000)
  private val processingCounter = AtomicInteger(0)

  private var baseDirectoriesSet: Set<VirtualFile> = emptySet()

  init {
    scope.launch {
      flow.collect { change ->
        try {
          updateTreeAndFireChanges(change)
        }
        finally {
          processingCounter.getAndDecrement()
        }
      }
    }

    project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        processingCounter.getAndIncrement()
        flow.tryEmit(event)
      }
    })

    synchronized(virtualFilesTree) {
      @Suppress("LeakingThis")
      collectRoots(WorkspaceModel.getInstance(project).currentSnapshot).forEach { virtualFilesTree.add(it) }
      baseDirectoriesSet = virtualFilesTree.getRoots()
    }
  }

  override val isProcessing: Boolean
    get() = processingCounter.get() != 0

  @RequiresBackgroundThread
  private suspend fun updateTreeAndFireChanges(change: VersionedStorageChange) {
    val oldPossibleRoots = hashSetOf<VirtualFile>()
    val newPossibleRoots = hashSetOf<VirtualFile>()
    processChange(change, oldPossibleRoots, newPossibleRoots)
    if (newPossibleRoots.isEmpty() && oldPossibleRoots.isEmpty()) return

    val oldRoots: Set<VirtualFile>
    val newRoots: Set<VirtualFile>

    synchronized(virtualFilesTree) {
      oldRoots = virtualFilesTree.getRoots()
      oldPossibleRoots.forEach { virtualFilesTree.remove(it) }
      newPossibleRoots.forEach { virtualFilesTree.add(it) }
      newRoots = virtualFilesTree.getRoots()
      baseDirectoriesSet = newRoots
    }

    val diff = BaseProjectDirectoriesDiff(oldRoots - newRoots, newRoots - oldRoots)
    if (diff.added.isEmpty() && diff.removed.isEmpty()) return

    withContext(Dispatchers.Main) {
      fireChange(diff)
    }
  }

  private fun ContentRootEntity.getBaseDirectory(): VirtualFile? {
    return url.virtualFile?.takeIf { it.isDirectory }
  }

  protected open fun collectRoots(snapshot: EntityStorageSnapshot): Sequence<VirtualFile> {
    return snapshot.entities(ContentRootEntity::class.java).mapNotNull { contentRootEntity ->
      contentRootEntity.getBaseDirectory()
    }
  }

  protected open fun processChange(change: VersionedStorageChange, oldRoots: HashSet<VirtualFile>, newRoots: HashSet<VirtualFile>) {
    change.getChanges(ContentRootEntity::class.java).forEach {
      it.oldEntity?.getBaseDirectory()?.let { virtualFile ->
        oldRoots.add(virtualFile)
      }
      it.newEntity?.getBaseDirectory()?.let { virtualFile ->
        newRoots.add(virtualFile)
      }
    }
  }

  override fun getBaseDirectories(): Set<VirtualFile> {
    return synchronized(virtualFilesTree) { baseDirectoriesSet }
  }

  override fun getBaseDirectoryFor(virtualFile: VirtualFile): VirtualFile? {
    val baseDirectories = getBaseDirectories()

    var current : VirtualFile? = virtualFile
    while (current != null) {
      if (baseDirectories.contains(current)) return current
      current = current.parent
    }

    return null
  }
}