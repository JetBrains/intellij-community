// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore

import com.intellij.dvcs.repo.AbstractRepositoryManager
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.Alarm
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

abstract class VcsRepositoryIgnoredFilesHolderBase<REPOSITORY : Repository>(
  @JvmField
  protected val repository: REPOSITORY,
  private val repositoryManager: AbstractRepositoryManager<REPOSITORY>,
  updateQueueName: String,
  private val rescanIdentityName: String
) : VcsRepositoryIgnoredFilesHolder, AsyncVfsEventsListener {

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

  override fun addFiles(files: Collection<VirtualFile>) {
    SET_LOCK.write { ignoredSet.addAll(files) }
  }

  override fun addFile(file: VirtualFile) {
    SET_LOCK.write { ignoredSet.add(file) }
  }

  override fun isInUpdateMode() = inUpdateMode.get()

  override fun getIgnoredFiles(): Set<VirtualFile> = SET_LOCK.read { ignoredSet.toHashSet() }

  override fun containsFile(file: VirtualFile) =
    SET_LOCK.read { isUnder(ignoredSet, file) }

  override fun getSize() = SET_LOCK.read { ignoredSet.size }

  override fun dispose() {
    SET_LOCK.write {
      ignoredSet.clear()
    }
  }

  override fun filesChanged(events: List<VFileEvent>) {
    val affectedFiles = events
      .asSequence()
      .mapNotNull(::getAffectedFile)
      .filter { repository.root == VcsUtil.getVcsRootFor(repository.project, it) }
      .map(VcsUtil::getFilePath)
      .toList()

    if (affectedFiles.isNotEmpty()) {
      removeIgnoredFiles(affectedFiles)
      checkIgnored(affectedFiles)
    }
  }

  fun setupVfsListener() =
    runReadAction {
      if (repository.project.isDisposed) return@runReadAction
      AsyncVfsEventsPostProcessor.getInstance().addListener(this, this)
    }

  protected abstract fun requestIgnored(paths: Collection<FilePath>? = null): Set<VirtualFile>

  protected abstract fun scanTurnedOff(): Boolean

  override fun startRescan() {
    if (scanTurnedOff()) return

    queueIgnoreUpdate(isFullRescan = true) {
      doRescan()
    }
  }

  override fun removeIgnoredFiles(filePaths: Collection<FilePath>): List<FilePath> {
    val removedIgnoredFiles = arrayListOf<FilePath>()
    val filePathsSet = filePaths.toHashSet()
    SET_LOCK.write {
      val iter = ignoredSet.iterator()
      while (iter.hasNext()) {
        val filePath = VcsUtil.getFilePath(iter.next())
        if (isUnder(filePathsSet, filePath)) {
          iter.remove()
          removedIgnoredFiles.add(filePath)
        }
      }
    }
    return removedIgnoredFiles
  }

  private fun checkIgnored(paths: Collection<FilePath>) {
    if (scanTurnedOff()) return

    queueIgnoreUpdate(isFullRescan = false) {
      doCheckIgnored(paths)
    }
  }

  private fun queueIgnoreUpdate(isFullRescan: Boolean, action: () -> Set<FilePath>) {
    updateQueue.queue(object : Update(rescanIdentityName) {
      override fun canEat(update: Update) = isFullRescan

      override fun run() {
        if (inUpdateMode.compareAndSet(false, true)) {
          fireUpdateStarted()
          val ignored = action()
          inUpdateMode.set(false)
          fireUpdateFinished(ignored)
        }
      }
    })
  }

  private fun doCheckIgnored(paths: Collection<FilePath>): Set<FilePath> {
    val ignored = requestIgnored(paths).filterByRepository(repository)
    addNotContainedIgnores(ignored)
    return ignored.map(VcsUtil::getFilePath).toSet()
  }

  private fun addNotContainedIgnores(ignored: Collection<VirtualFile>) =
    SET_LOCK.write {
      ignored.forEach { ignored ->
        if (!isUnder(ignoredSet, ignored)) {
          ignoredSet.add(ignored)
        }
      }
    }

  private fun doRescan(): Set<FilePath> {
    val ignored = requestIgnored().filterByRepository(repository)
    SET_LOCK.write {
      ignoredSet.clear()
      ignoredSet.addAll(ignored)
    }
    return ignored.map(VcsUtil::getFilePath).toSet()
  }

  private fun <REPOSITORY> Set<VirtualFile>.filterByRepository(repository: REPOSITORY) =
    filter { repositoryManager.getRepositoryForFileQuick(it) == repository }

  private fun fireUpdateStarted() {
    listeners.multicaster.updateStarted()
  }

  private fun fireUpdateFinished(paths: Collection<FilePath>) {
    listeners.multicaster.updateFinished(paths)
  }

  private fun isUnder(parents: Set<VirtualFile>, child: VirtualFile) = generateSequence(child) { it.parent }.any { it in parents }
  private fun isUnder(parents: Set<FilePath>, child: FilePath) = generateSequence(child) { it.parentPath }.any { it in parents }

  companion object {
    @JvmStatic
    fun getAffectedFile(event: VFileEvent): VirtualFile? =
      runReadAction {
        when {
          event is VFileCreateEvent && event.parent.isValid -> event.file
          event is VFileDeleteEvent || event is VFileMoveEvent || event.isRename() -> event.file
          event is VFileCopyEvent && event.newParent.isValid -> event.newParent.findChild(event.newChildName)
          else -> null
        }
      }

    private fun VFileEvent.isRename() = this is VFilePropertyChangeEvent && propertyName == VirtualFile.PROP_NAME
  }
}
