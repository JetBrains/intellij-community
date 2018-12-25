// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.vcsUtil.VcsUtil
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

abstract class VcsRepositoryIgnoredFilesHolderBase<REPOSITORY : Repository>(
  @JvmField
  protected val repository: REPOSITORY,
  updateQueueName: String,
  private val rescanIdentityName: String
) : VcsRepositoryIgnoredFilesHolder {

  private val inUpdateMode = AtomicBoolean(false)
  private val updateQueue = MergingUpdateQueue(updateQueueName, 500, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD)
  private val ignoredSet = hashSetOf<VirtualFile>()
  private val SET_LOCK = ReentrantReadWriteLock()
  private val listeners = EventDispatcher.create(VcsIgnoredHolderUpdateListener::class.java)

  init {
    Disposer.register(this, updateQueue)
  }

  override fun addUpdateStateListener(listener: VcsIgnoredHolderUpdateListener) {
    listeners.addListener(listener, this)
  }

  override fun addFiles(files: Collection<VirtualFile>) { SET_LOCK.write { ignoredSet.addAll(files) } }

  override fun addFile(file: VirtualFile) { SET_LOCK.write { ignoredSet.add(file) } }

  override fun isInUpdateMode() = inUpdateMode.get()

  override fun getIgnoredFiles(): Set<VirtualFile> = SET_LOCK.read { ignoredSet.toHashSet() }

  override fun containsFile(file: VirtualFile) =
    SET_LOCK.read { ignoredSet.any { ignoredFile -> VfsUtil.isAncestor(ignoredFile, file, false) } }

  override fun getSize() = SET_LOCK.read { ignoredSet.size }

  override fun dispose() {
    SET_LOCK.write {
      ignoredSet.clear()
    }
  }

  protected abstract fun requestIgnored(ignoreFilePath: String?): Set<VirtualFile>

  protected abstract fun scanTurnedOff(): Boolean

  override fun startRescan(ignoreFilePath: String?) {
    if (scanTurnedOff()) return

    updateQueue.queue(object : Update(rescanIdentityName) {
      override fun canEat(update: Update) = true

      override fun run() {
        if (inUpdateMode.compareAndSet(false, true)) {
          fireUpdateStarted()
          doRescan()
          inUpdateMode.set(false)
          fireUpdateFinished()
        }
      }
    })
  }

  private fun doRescan(ignoreFilePath: String? = null) {
    val ignored = when {
      ignoreFilePath == null -> requestIgnored(null)
      isIgnoreFileUnderRepositoryRoot(ignoreFilePath)-> requestIgnored(ignoreFilePath)
      else -> return
    }
    SET_LOCK.write {
      ignoredSet.clear()
      ignoredSet.addAll(ignored)
    }
  }

  private fun isIgnoreFileUnderRepositoryRoot(ignoreFilePath: String): Boolean {
    return repository.root == VcsUtil.getVcsRootFor(repository.project, LocalFileSystem.getInstance().findFileByPath(ignoreFilePath))
  }

  private fun fireUpdateStarted(ignoreFilePath: String? = null) {
    when (ignoreFilePath) {
      null -> listeners.multicaster.updateStarted()
      else -> listeners.multicaster.updateStarted(ignoreFilePath)
    }
  }

  private fun fireUpdateFinished(ignoreFilePath: String? = null) {
    when (ignoreFilePath) {
      null -> listeners.multicaster.updateFinished()
      else -> listeners.multicaster.updateFinished(ignoreFilePath)
    }
  }

  override fun removeIgnoredFiles(files: Collection<FilePath>): List<FilePath> {
    val removedIgnoredFiles = arrayListOf<FilePath>()
    SET_LOCK.write {
      val iter = ignoredSet.iterator()
      while (iter.hasNext()) {
        val filePath = VcsUtil.getFilePath(iter.next())
        if (files.contains(filePath)) {
          iter.remove()
          removedIgnoredFiles.add(filePath)
        }
      }
    }
    return removedIgnoredFiles
  }
}
