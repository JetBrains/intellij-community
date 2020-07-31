// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore

import com.intellij.dvcs.repo.AbstractRepositoryManager
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsIgnoreManagerImpl
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.EventDispatcher
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.ComparableObject
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.Update
import com.intellij.vcsUtil.VcsFileUtilKt.isUnder
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val LOG = logger<VcsRepositoryIgnoredFilesHolderBase<*>>()

abstract class VcsRepositoryIgnoredFilesHolderBase<REPOSITORY : Repository>(
  @JvmField
  protected val repository: REPOSITORY,
  private val repositoryManager: AbstractRepositoryManager<REPOSITORY>
) : VcsRepositoryIgnoredFilesHolder, AsyncVfsEventsListener, ChangeListListener {

  private val inUpdateMode = AtomicBoolean(false)
  private val updateQueue = VcsIgnoreManagerImpl.getInstanceImpl(repository.project).ignoreRefreshQueue
  private val ignoredSet = hashSetOf<FilePath>()
  private val unprocessedFiles = hashSetOf<FilePath>()
  private val SET_LOCK = ReentrantReadWriteLock()
  private val UNPROCESSED_FILES_LOCK = ReentrantReadWriteLock()
  private val listeners = EventDispatcher.create(VcsIgnoredHolderUpdateListener::class.java)
  private val repositoryRootPath = VcsUtil.getFilePath(repository.root)

  override fun addUpdateStateListener(listener: VcsIgnoredHolderUpdateListener) {
    listeners.addListener(listener, this)
  }

  override fun addFiles(files: Collection<FilePath>) {
    SET_LOCK.write { ignoredSet.addAll(files) }
  }

  override fun addFile(file: FilePath) {
    SET_LOCK.write { ignoredSet.add(file) }
  }

  override fun isInUpdateMode() = inUpdateMode.get()

  override fun getIgnoredFilePaths(): Set<FilePath> = SET_LOCK.read { ignoredSet.toHashSet() }

  override fun containsFile(file: FilePath) =
    SET_LOCK.read { isUnder(repositoryRootPath, ignoredSet, file) }

  override fun getSize() = SET_LOCK.read { ignoredSet.size }

  override fun dispose() {
    SET_LOCK.write {
      ignoredSet.clear()
    }
  }

  override fun changeListUpdateDone() {
    if (scanTurnedOff()) return

    val filesToCheck = UNPROCESSED_FILES_LOCK.read { unprocessedFiles.toHashSet() }

    UNPROCESSED_FILES_LOCK.write {
      unprocessedFiles.removeAll(filesToCheck)
    }
    //if the files already unversioned, there is no need to check it for ignore
    val unversioned = ChangeListManagerImpl.getInstanceImpl(repository.project).unversionedFilesPaths
    filesToCheck.removeAll(unversioned)

    if (filesToCheck.isNotEmpty()) {
      removeIgnoredFiles(filesToCheck)
      checkIgnored(filesToCheck)
    }
  }

  override fun filesChanged(events: List<VFileEvent>) {
    if (scanTurnedOff()) return

    val affectedFiles = events
      .flatMap(::getAffectedFilePaths)
      .filter { repository.root == VcsUtil.getVcsRootFor(repository.project, it) }
      .toList()

    UNPROCESSED_FILES_LOCK.write {
      unprocessedFiles.addAll(affectedFiles)
    }
  }

  fun setupListeners() =
    runReadAction {
      if (repository.project.isDisposed) return@runReadAction
      AsyncVfsEventsPostProcessor.getInstance().addListener(this, this)
      repository.project.messageBus.connect(this).subscribe(ChangeListListener.TOPIC, this)
    }

  @Throws(VcsException::class)
  protected abstract fun requestIgnored(paths: Collection<FilePath>? = null): Set<FilePath>

  private fun tryRequestIgnored(paths: Collection<FilePath>? = null): Set<FilePath> =
    try {
      requestIgnored(paths)
    }
    catch (e: VcsException) {
      LOG.warn("Cannot request ignored: ", e)
      emptySet()
    }

  protected abstract fun scanTurnedOff(): Boolean

  override fun startRescan() {
    startRescan(null)
  }

  override fun startRescan(actionAfterRescan: Runnable?) {
    if (scanTurnedOff()) return

    queueIgnoreUpdate(isFullRescan = true, doAfterRescan = actionAfterRescan) {
      doRescan()
    }
  }

  override fun removeIgnoredFiles(filePaths: Collection<FilePath>): Collection<FilePath> {
    val removedIgnoredFilePaths = hashSetOf<FilePath>()
    val filePathsSet = filePaths.toHashSet()
    val ignored = SET_LOCK.read { ignoredSet.toHashSet() }

    for (filePath in ignored) {
      if (isUnder(repositoryRootPath, filePathsSet, filePath)) {
        removedIgnoredFilePaths.add(filePath)
      }
    }

    SET_LOCK.write {
      ignoredSet.removeAll(removedIgnoredFilePaths)
    }
    return removedIgnoredFilePaths
  }

  private fun checkIgnored(paths: Collection<FilePath>) {
    if (scanTurnedOff()) return

    queueIgnoreUpdate(isFullRescan = false) {
      doCheckIgnored(paths)
    }
  }

  private fun queueIgnoreUpdate(isFullRescan: Boolean, doAfterRescan: Runnable? = null, action: () -> Set<FilePath>) {
    //full rescan should have the same update identity, so multiple full rescans can be swallowed instead of spawning new threads
    updateQueue.queue(MyUpdate(repository, isFullRescan) {
      if (inUpdateMode.compareAndSet(false, true)) {
        fireUpdateStarted()
        val ignored = action()
        inUpdateMode.set(false)
        fireUpdateFinished(ignored, isFullRescan)
        doAfterRescan?.run()
      }
    })
  }

  private class MyUpdate(val repository: Repository,
                         val isFullRescan: Boolean,
                         val action: () -> Unit)
    : DisposableUpdate(repository, ComparableObject.Impl(repository, isFullRescan)) {

    override fun canEat(update: Update) = update is MyUpdate &&
                                          update.repository == repository &&
                                          isFullRescan

    override fun doRun() = action()
  }

  private fun doCheckIgnored(paths: Collection<FilePath>): Set<FilePath> {
    val ignored = tryRequestIgnored(paths).filterByRepository(repository)
    LOG.debug("Check ignored for paths: ", paths)
    LOG.debug("Ignored found for paths: ", ignored)
    addNotContainedIgnores(ignored)
    return ignored.toSet()
  }

  private fun addNotContainedIgnores(ignored: Collection<FilePath>) =
    SET_LOCK.write {
      ignored.forEach { ignored ->
        if (!isUnder(repositoryRootPath, ignoredSet, ignored)) {
          ignoredSet.add(ignored)
        }
      }
    }

  private fun doRescan(): Set<FilePath> {
    val ignored = tryRequestIgnored().filterByRepository(repository)
    LOG.debug("Full ignore rescan executed. Found ignores: ", ignored)
    SET_LOCK.write {
      ignoredSet.clear()
      ignoredSet.addAll(ignored)
    }
    return ignored.toSet()
  }

  private fun <REPOSITORY> Set<FilePath>.filterByRepository(repository: REPOSITORY) =
    filter { repositoryManager.getRepositoryForFileQuick(it) == repository }

  private fun fireUpdateStarted() {
    listeners.multicaster.updateStarted()
  }

  private fun fireUpdateFinished(paths: Collection<FilePath>, isFullRescan: Boolean) {
    listeners.multicaster.updateFinished(paths, isFullRescan)
  }

  @TestOnly
  inner class Waiter : VcsIgnoredHolderUpdateListener {

    private val awaitLatch = CountDownLatch(1)

    init {
      addUpdateStateListener(this)
    }

    override fun updateFinished(ignoredPaths: Collection<FilePath>, isFullRescan: Boolean) = awaitLatch.countDown()

    fun waitFor() {
      awaitLatch.await()
      listeners.removeListener(this)
    }
  }

  @TestOnly
  fun startRescanAndWait() {
    assert(ApplicationManager.getApplication().isUnitTestMode)
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()
    updateQueue.flush()
    while (updateQueue.isFlushing) {
      TimeoutUtil.sleep(100)
    }
    val waiter = createWaiter()
    AppExecutorUtil.getAppScheduledExecutorService().schedule({ startRescan() }, 1, TimeUnit.SECONDS)
    waiter.waitFor()
  }

  @TestOnly
  fun createWaiter(): Waiter {
    assert(ApplicationManager.getApplication().isUnitTestMode)
    return Waiter()
  }

  companion object {
    @JvmStatic
    fun getAffectedFilePaths(event: VFileEvent): Set<FilePath> {
      if (event is VFileContentChangeEvent) return emptySet()

      val affectedFilePaths = HashSet<FilePath>(2)
      val isDirectory = if (event is VFileCreateEvent) event.isDirectory else event.file!!.isDirectory

      affectedFilePaths.add(VcsUtil.getFilePath(event.path, isDirectory))

      if (event is VFileMoveEvent) {
        affectedFilePaths.add(VcsUtil.getFilePath(event.oldPath, isDirectory))
      }
      else if (event is VFilePropertyChangeEvent && event.isRename) {
        affectedFilePaths.add(VcsUtil.getFilePath(event.oldPath, isDirectory))
      }

      return affectedFilePaths
    }
  }
}
