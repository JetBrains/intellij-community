// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.dvcs.repo

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.openapi.vcs.impl.VcsStartupActivity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * VcsRepositoryManager creates, stores and updates all repository's information using registered [VcsRepositoryCreator]
 * extension point in a thread safe way.
 */
@Service(Service.Level.PROJECT)
class VcsRepositoryManager @ApiStatus.Internal constructor(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : Disposable {
  private val vcsManager = ProjectLevelVcsManager.getInstance(project)

  private val REPO_LOCK = ReentrantReadWriteLock()
  private val MODIFY_LOCK = ReentrantReadWriteLock().writeLock()

  private val repositories = HashMap<VirtualFile, Repository>()
  private val externalRepositories = HashMap<VirtualFile, Repository>()
  private val pathToRootMap: MutableMap<String, VirtualFile> = CollectionFactory.createFilePathMap()

  private val updateAlarm: Alarm

  @Volatile
  private var isDisposed = false

  private val isStarted = AtomicBoolean(false)
  private val updateScheduled = AtomicBoolean(false)

  init {
    project.messageBus.connect(coroutineScope).subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
                                                         VcsMappingListener { this.scheduleUpdate() })

    EP_NAME.addChangeListener(coroutineScope) {
      disposeAllRepositories(false)
      scheduleUpdate()
      project.messageBus.syncPublisher(VCS_REPOSITORY_MAPPING_UPDATED).mappingChanged()
    }
    updateAlarm = Alarm(
      threadToUse = Alarm.ThreadToUse.POOLED_THREAD,
      parentDisposable = null,
      activationComponent = null,
      coroutineScope = coroutineScope,
    )
  }

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<VcsRepositoryCreator> = ExtensionPointName("com.intellij.vcsRepositoryCreator")

    private val LOG = logger<VcsRepositoryManager>()

    /**
     * VCS repository mapping updated. Project level.
     */
    @Topic.ProjectLevel
    @JvmField
    val VCS_REPOSITORY_MAPPING_UPDATED: Topic<VcsRepositoryMappingListener> = Topic(
      VcsRepositoryMappingListener::class.java, Topic.BroadcastDirection.NONE)

    @JvmStatic
    fun getInstance(project: Project): VcsRepositoryManager = project.service()

    private fun tryCreateRepository(
      project: Project,
      vcs: AbstractVcs,
      rootPath: VirtualFile,
      disposable: Disposable
    ): Repository? {
      return EP_NAME.computeSafeIfAny { creator ->
        if (creator.vcsKey == vcs.keyInstanceMethod) {
          return@computeSafeIfAny creator.createRepositoryIfValid(project, rootPath, disposable)
        }
        null
      }
    }
  }

  internal class MyStartupActivity : VcsStartupActivity {
    override suspend fun execute(project: Project) {
      // run initial refresh and wait its completion
      // to make sure VcsInitObject.AFTER_COMMON are being run with initialized repositories
      project.serviceAsync<VcsRepositoryManager>().ensureUpToDate()
    }

    override val order: Int
      get() = VcsInitObject.OTHER_INITIALIZATION.order
  }

  override fun dispose() {
    isDisposed = true
    disposeAllRepositories(true)
  }

  private fun disposeAllRepositories(disposeExternal: Boolean) {
    REPO_LOCK.writeLock().lock()
    try {
      for (repo in repositories.values) {
        Disposer.dispose(repo)
      }
      repositories.clear()

      if (disposeExternal) {
        for (repo in externalRepositories.values) {
          Disposer.dispose(repo)
        }
        externalRepositories.clear()
      }

      updatePathToRootMap()
    }
    finally {
      REPO_LOCK.writeLock().unlock()
    }
  }

  private fun scheduleUpdate() {
    if (!isStarted.get() || isDisposed) return
    if (updateScheduled.compareAndSet(false, true)) {
      updateAlarm.addRequest({ checkAndUpdateRepositoryCollection(null) }, 100)
    }
  }

  @ApiStatus.Internal
  suspend fun ensureUpToDate() {
    if (isStarted.compareAndSet(false, true)) {
      updateScheduled.set(true)
      updateAlarm.addRequest({ checkAndUpdateRepositoryCollection(null) }, 0)
    }

    val waiter = CompletableDeferred<Unit>(parent = coroutineScope.coroutineContext.job)
    updateAlarm.addRequest(request = { waiter.complete(Unit) }, delayMillis = 10)
    waiter.join()
  }

  @RequiresBackgroundThread
  fun getRepositoryForFile(file: VirtualFile?): Repository? = getRepositoryForFile(file = file, quick = false)

  @CalledInAny
  fun getRepositoryForFileQuick(file: VirtualFile?): Repository? = getRepositoryForFile(file = file, quick = true)

  fun getRepositoryForFile(file: VirtualFile?, quick: Boolean): Repository? {
    val vcsRoot = vcsManager.getVcsRootObjectFor(file) ?: return getExternalRepositoryForFile(file)
    return if (quick) getRepositoryForRootQuick(vcsRoot.path) else getRepositoryForRoot(vcsRoot.path)
  }

  fun getRepositoryForFile(file: FilePath?, quick: Boolean): Repository? {
    val vcsRoot = vcsManager.getVcsRootObjectFor(file) ?: return getExternalRepositoryForFile(file)
    return if (quick) getRepositoryForRootQuick(vcsRoot.path) else getRepositoryForRoot(vcsRoot.path)
  }

  fun getExternalRepositoryForFile(file: VirtualFile?): Repository? {
    if (file == null) {
      return null
    }

    val repositories = getExternalRepositories()
    for ((key, value) in repositories) {
      if (key.isValid && VfsUtilCore.isAncestor(key, file, false)) {
        return value
      }
    }
    return null
  }

  fun getExternalRepositoryForFile(file: FilePath?): Repository? {
    if (file == null) {
      return null
    }

    val repositories = getExternalRepositories()
    for ((key, value) in repositories) {
      if (key.isValid && FileUtil.isAncestor(key.path, file.path, false)) {
        return value
      }
    }
    return null
  }

  fun getRepositoryForRootQuick(rootPath: FilePath?): Repository? {
    return getRepositoryForRoot(root = getVirtualFileForRoot(rootPath) ?: return null, updateIfNeeded = false)
  }

  private fun getVirtualFileForRoot(rootPath: FilePath?): VirtualFile? {
    if (rootPath == null) {
      return null
    }

    REPO_LOCK.readLock().lock()
    try {
      return pathToRootMap.get(rootPath.path)
    }
    finally {
      REPO_LOCK.readLock().unlock()
    }
  }

  private fun updatePathToRootMap() {
    pathToRootMap.clear()
    for (root in repositories.keys) {
      pathToRootMap.put(root.path, root)
    }
    for (root in externalRepositories.keys) {
      pathToRootMap.put(root.path, root)
    }
  }

  fun getRepositoryForRootQuick(root: VirtualFile?): Repository? = getRepositoryForRoot(root = root, updateIfNeeded = false)

  fun getRepositoryForRoot(root: VirtualFile?): Repository? = getRepositoryForRoot(root = root, updateIfNeeded = true)

  private fun getRepositoryForRoot(root: VirtualFile?, updateIfNeeded: Boolean): Repository? {
    if (root == null) {
      return null
    }

    @Suppress("NAME_SHADOWING")
    var updateIfNeeded = updateIfNeeded
    val app = ApplicationManager.getApplication()
    if (updateIfNeeded && app.isDispatchThread && !app.isUnitTestMode && !app.isHeadlessEnvironment) {
      updateIfNeeded = false
      LOG.error("Do not call synchronous repository update in EDT")
    }

    REPO_LOCK.readLock().lock()
    try {
      if (isDisposed) {
        throw ProcessCanceledException()
      }
      (repositories.get(root) ?: externalRepositories.get(root))?.let {
        return it
      }
    }
    finally {
      REPO_LOCK.readLock().unlock()
    }

    // if we didn't find the appropriate repository, request update mappings if needed and try again
    // may be this should not be called from several places (for example, branch widget updating from edt).
    return if (updateIfNeeded && vcsManager.allVersionedRoots.contains(root)) {
      checkAndUpdateRepositoryCollection(root)

      REPO_LOCK.readLock().lock()
      try {
        repositories.get(root)
      }
      finally {
        REPO_LOCK.readLock().unlock()
      }
    }
    else {
      null
    }
  }

  fun addExternalRepository(root: VirtualFile, repository: Repository) {
    REPO_LOCK.writeLock().lock()
    try {
      externalRepositories.put(root, repository)
      updatePathToRootMap()
    }
    finally {
      REPO_LOCK.writeLock().unlock()
    }
  }

  fun removeExternalRepository(root: VirtualFile) {
    REPO_LOCK.writeLock().lock()
    try {
      externalRepositories.remove(root)
      updatePathToRootMap()
    }
    finally {
      REPO_LOCK.writeLock().unlock()
    }
  }

  fun isExternal(repository: Repository): Boolean {
    REPO_LOCK.readLock().lock()
    try {
      return !repositories.containsValue(repository) && externalRepositories.containsValue(repository)
    }
    finally {
      REPO_LOCK.readLock().unlock()
    }
  }

  fun getRepositories(): Collection<Repository> {
    REPO_LOCK.readLock().lock()
    try {
      return java.util.List.copyOf(repositories.values)
    }
    finally {
      REPO_LOCK.readLock().unlock()
    }
  }

  private fun getExternalRepositories(): Map<VirtualFile, Repository> {
    REPO_LOCK.readLock().lock()
    try {
      return java.util.Map.copyOf(externalRepositories)
    }
    finally {
      REPO_LOCK.readLock().unlock()
    }
  }

  @RequiresBackgroundThread
  private fun checkAndUpdateRepositoryCollection(checkedRoot: VirtualFile?) {
    updateScheduled.set(false)

    if (MODIFY_LOCK.isHeldByCurrentThread) {
      LOG.error(Throwable("Recursive Repository initialization"))
      return
    }

    MODIFY_LOCK.lock()
    try {
      REPO_LOCK.readLock().lock()
      val repositoryListSnapshot = try {
        HashMap(repositories)
      }
      finally {
        REPO_LOCK.readLock().unlock()
      }

      if (checkedRoot != null && repositoryListSnapshot.containsKey(checkedRoot)) {
        return
      }

      BackgroundTaskUtil.runUnderDisposeAwareIndicator(this) {
        val invalidRoots = findInvalidRoots(repositoryListSnapshot.values)
        for (invalidRoot in invalidRoots) {
          repositoryListSnapshot.keys.remove(invalidRoot)
        }
        repositoryListSnapshot.putAll(findNewRoots(repositoryListSnapshot.keys))
      }

      REPO_LOCK.writeLock().lock()
      try {
        if (!isDisposed) {
          for ((file, oldRepo) in repositories) {
            val newRepo = repositoryListSnapshot.get(file)
            if (oldRepo !== newRepo) {
              Disposer.dispose(oldRepo)
            }
          }

          repositories.clear()
          repositories.putAll(repositoryListSnapshot)
        }

        updatePathToRootMap()
      }
      finally {
        REPO_LOCK.writeLock().unlock()
      }
    }
    finally {
      MODIFY_LOCK.unlock()
    }
    project.messageBus.syncPublisher(VCS_REPOSITORY_MAPPING_UPDATED).mappingChanged()
  }

  private fun findNewRoots(knownRoots: Set<VirtualFile>): Map<VirtualFile, Repository> {
    val newRootsMap = HashMap<VirtualFile, Repository>()
    for (root in vcsManager.allVcsRoots) {
      val rootPath = root.path
      if (!knownRoots.contains(rootPath)) {
        val repository = root.vcs?.let {
          tryCreateRepository(project = project, vcs = it, rootPath = rootPath, disposable = this)
        } ?: continue
        newRootsMap.put(rootPath, repository)
      }
    }
    return newRootsMap
  }

  private fun findInvalidRoots(repositories: Collection<Repository>): Collection<VirtualFile> {
    val invalidRepos = ArrayList<VirtualFile>()
    for (repo in repositories) {
      val vcsRoot = vcsManager.getVcsRootObjectFor(repo.root)
      if (vcsRoot == null || repo.root != vcsRoot.path || repo.vcs != vcsRoot.vcs) {
        invalidRepos.add(repo.root)
      }
    }
    return invalidRepos
  }

  override fun toString(): String = "RepositoryManager(repositories=$repositories)" // NON-NLS

  @TestOnly
  fun waitForAsyncTaskCompletion() {
    updateAlarm.waitForAllExecuted(10, TimeUnit.SECONDS)
  }
}
