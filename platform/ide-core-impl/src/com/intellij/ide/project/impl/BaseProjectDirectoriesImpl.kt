// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.project.impl

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.BaseProjectDirectoriesDiff
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePrefixTree
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

open class BaseProjectDirectoriesImpl(val project: Project, scope: CoroutineScope) : BaseProjectDirectories(project) {

  private val virtualFilesTree = VirtualFilePrefixTree.createSet()
  private val processingCounter = AtomicInteger(0)

  private var baseDirectoriesSet: Set<VirtualFile> = emptySet()
  private val rwLock = ReentrantReadWriteLock()

  init {
    scope.launch {
      project.serviceAsync<WorkspaceModel>().eventLog.collect { event ->
        processingCounter.getAndIncrement()
        try {
          updateTreeAndFireChanges(event)
        }
        finally {
          processingCounter.getAndDecrement()
        }
      }
    }

    rwLock.write {
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

    rwLock.write {
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

  protected open fun collectRoots(snapshot: ImmutableEntityStorage): Sequence<VirtualFile> {
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
    return rwLock.read { baseDirectoriesSet }
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