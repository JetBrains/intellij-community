// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.GeneralSettings
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.RefreshSession
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class SaveAndSyncHandlerScheduledRefreshTest {
  @BeforeEach
  fun `set settings`() {
    GeneralSettings.getInstance().apply {
      isBackgroundSync = false
      isSyncOnFrameActivation = true
      isSaveOnFrameDeactivation = false
      isAutoSaveIfInactive = false
    }
  }

  @Test
  fun `scheduled scoped refresh refreshes requested root only`(): Unit = timeoutRunBlocking(10.seconds) {
    val requestedDir = Files.createTempDirectory("scheduled-scoped-refresh-requested")
    val skippedDir = Files.createTempDirectory("scheduled-scoped-refresh-skipped")

    try {
      withSaveAndSyncHandler { handler ->
        val requestedRoot = loadDirectory(requestedDir)
        val skippedRoot = loadDirectory(skippedDir)

        requestedDir.resolve("created.txt").writeText("requested")
        skippedDir.resolve("created.txt").writeText("skipped")
        VfsUtil.markDirty(false, false, requestedRoot, skippedRoot)

        handler.scheduleRefresh(listOf(requestedDir))

        waitUntil("Scoped scheduled VFS refresh did not refresh requested root", timeout = 5.seconds) {
          requestedRoot.findChild("created.txt") != null
        }
        delay(500.milliseconds)

        assertThat(skippedRoot.findChild("created.txt")).isNull()
      }
    }
    finally {
      NioFiles.deleteRecursively(requestedDir)
      NioFiles.deleteRecursively(skippedDir)
    }
  }

  @Test
  fun `scheduled scoped refresh coalesces pending paths`(): Unit = timeoutRunBlocking(10.seconds) {
    val firstDir = Files.createTempDirectory("scheduled-scoped-refresh-first")
    val secondDir = Files.createTempDirectory("scheduled-scoped-refresh-second")

    try {
      withScopedVfsRefreshScheduler(BlockingRefreshQueue()) { scheduler, refreshQueue ->
        val firstRoot = loadDirectory(firstDir)
        val secondRoot = loadDirectory(secondDir)

        scheduler.schedule(listOf(firstDir))
        scheduler.schedule(listOf(secondDir))

        assertThat(refreshQueue.firstRefreshStarted.await()).containsExactlyInAnyOrder(firstRoot, secondRoot)
        assertThat(refreshQueue.refreshCount()).isEqualTo(1)

        refreshQueue.releaseFirstRefresh.complete(Unit)
        delay(700.milliseconds)

        assertThat(refreshQueue.refreshCount()).isEqualTo(1)
      }
    }
    finally {
      NioFiles.deleteRecursively(firstDir)
      NioFiles.deleteRecursively(secondDir)
    }
  }

  @Test
  fun `scheduled scoped refresh coalesces paths scheduled during in-flight refresh`(): Unit = timeoutRunBlocking(10.seconds) {
    val firstDir = Files.createTempDirectory("scheduled-scoped-refresh-in-flight-first")
    val secondDir = Files.createTempDirectory("scheduled-scoped-refresh-in-flight-second")
    val thirdDir = Files.createTempDirectory("scheduled-scoped-refresh-in-flight-third")

    try {
      withScopedVfsRefreshScheduler(BlockingRefreshQueue()) { scheduler, refreshQueue ->
        val firstRoot = loadDirectory(firstDir)
        val secondRoot = loadDirectory(secondDir)
        val thirdRoot = loadDirectory(thirdDir)

        scheduler.schedule(listOf(firstDir))
        assertThat(refreshQueue.firstRefreshStarted.await()).containsExactly(firstRoot)

        scheduler.schedule(listOf(secondDir))
        scheduler.schedule(listOf(thirdDir))
        delay(700.milliseconds)

        assertThat(refreshQueue.refreshCount()).isEqualTo(1)

        refreshQueue.releaseFirstRefresh.complete(Unit)

        assertThat(refreshQueue.secondRefreshStarted.await()).containsExactlyInAnyOrder(secondRoot, thirdRoot)
        assertThat(refreshQueue.refreshCount()).isEqualTo(2)
      }
    }
    finally {
      NioFiles.deleteRecursively(firstDir)
      NioFiles.deleteRecursively(secondDir)
      NioFiles.deleteRecursively(thirdDir)
    }
  }

  @Test
  fun `scheduled scoped refresh refreshes same path requested during in-flight refresh again`(): Unit = timeoutRunBlocking(10.seconds) {
    val requestedDir = Files.createTempDirectory("scheduled-scoped-refresh-in-flight-same")

    try {
      withScopedVfsRefreshScheduler(BlockingRefreshQueue()) { scheduler, refreshQueue ->
        val requestedRoot = loadDirectory(requestedDir)

        scheduler.schedule(listOf(requestedDir))
        assertThat(refreshQueue.firstRefreshStarted.await()).containsExactly(requestedRoot)

        scheduler.schedule(listOf(requestedDir))
        delay(700.milliseconds)

        assertThat(refreshQueue.refreshCount()).isEqualTo(1)

        refreshQueue.releaseFirstRefresh.complete(Unit)

        assertThat(refreshQueue.secondRefreshStarted.await()).containsExactly(requestedRoot)
        assertThat(refreshQueue.refreshCount()).isEqualTo(2)
      }
    }
    finally {
      NioFiles.deleteRecursively(requestedDir)
    }
  }

  @Test
  fun `scheduled scoped refresh retries retained paths after sync unblock`(): Unit = timeoutRunBlocking(10.seconds) {
    val requestedDir = Files.createTempDirectory("scheduled-scoped-refresh-blocked")

    try {
      withSaveAndSyncHandler { handler ->
        val requestedRoot = loadDirectory(requestedDir)

        handler.blockSyncOnFrameActivation()
        var blocked = true
        try {
          requestedDir.resolve("created.txt").writeText("requested")
          VfsUtil.markDirty(false, false, requestedRoot)

          handler.scheduleRefresh(listOf(requestedDir))
          delay(700.milliseconds)

          assertThat(requestedRoot.findChild("created.txt")).isNull()

          handler.unblockSyncOnFrameActivation()
          blocked = false

          waitUntil("Scoped scheduled VFS refresh did not run after sync unblock", timeout = 5.seconds) {
            requestedRoot.findChild("created.txt") != null
          }
        }
        finally {
          if (blocked) {
            handler.unblockSyncOnFrameActivation()
          }
        }
      }
    }
    finally {
      NioFiles.deleteRecursively(requestedDir)
    }
  }

  @Test
  fun `scheduled scoped refresh drops pending paths when sync is disabled`(): Unit = timeoutRunBlocking(10.seconds) {
    val requestedDir = Files.createTempDirectory("scheduled-scoped-refresh-disabled")

    try {
      withSaveAndSyncHandler { handler ->
        val requestedRoot = loadDirectory(requestedDir)

        GeneralSettings.getInstance().isSyncOnFrameActivation = false
        try {
          requestedDir.resolve("created.txt").writeText("requested")
          VfsUtil.markDirty(false, false, requestedRoot)

          handler.scheduleRefresh(listOf(requestedDir))
          delay(700.milliseconds)

          assertThat(requestedRoot.findChild("created.txt")).isNull()
        }
        finally {
          GeneralSettings.getInstance().isSyncOnFrameActivation = true
        }

        delay(700.milliseconds)

        assertThat(requestedRoot.findChild("created.txt")).isNull()
      }
    }
    finally {
      NioFiles.deleteRecursively(requestedDir)
    }
  }

  @Test
  fun `suppressing periodic refresh does not block scheduled scoped refresh`(): Unit = timeoutRunBlocking(120.seconds) {
    // Regression guard for RIDER-139430: suppressPeriodicRefresh must NOT block the
    // frame-activation / scheduled refresh path (unlike blockSyncOnFrameActivation).
    val requestedDir = Files.createTempDirectory("suppress-periodic-scoped-refresh")

    try {
      withSaveAndSyncHandler { handler ->
        val requestedRoot = loadDirectory(requestedDir)

        handler.suppressPeriodicRefresh("test").use {
          requestedDir.resolve("created.txt").writeText("requested")
          VfsUtil.markDirty(false, false, requestedRoot)

          handler.scheduleRefresh(listOf(requestedDir))

          waitUntil("Scoped scheduled VFS refresh did not run while periodic refresh suppressed", timeout = 60.seconds) {
            requestedRoot.findChild("created.txt") != null
          }
        }
      }
    }
    finally {
      NioFiles.deleteRecursively(requestedDir)
    }
  }

  @Test
  fun `newer pending same path supersedes older unstarted path`() {
    val path = Path.of("project")

    assertThat(dropPathsCoveredByNewerRequests(paths = listOf(path), newerPaths = listOf(path))).isEmpty()
  }

  @Test
  fun `newer pending ancestor supersedes older unstarted descendant`() {
    val root = Path.of("project")
    val child = root.resolve("src")

    assertThat(dropPathsCoveredByNewerRequests(paths = listOf(child), newerPaths = listOf(root))).isEmpty()
  }

  @Test
  fun `newer pending descendant does not supersede older unstarted ancestor`() {
    val root = Path.of("project")
    val child = root.resolve("src")

    // The result is normalized once here so the downstream compaction does not normalize again.
    assertThat(dropPathsCoveredByNewerRequests(paths = listOf(root), newerPaths = listOf(child)))
      .containsExactly(root.toAbsolutePath().normalize())
  }

  @Test
  fun `newer pending sibling does not supersede older unstarted path`() {
    val root = Path.of("project")
    val firstChild = root.resolve("src")
    val secondChild = root.resolve("test")

    assertThat(dropPathsCoveredByNewerRequests(paths = listOf(firstChild), newerPaths = listOf(secondChild)))
      .containsExactly(firstChild.toAbsolutePath().normalize())
  }

  @Test
  fun `scoped refresh rejects non-positive batch size`() {
    assertThatThrownBy { ScopedVfsRefreshScheduler(refreshBatchLimit = 0) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessage("refreshBatchLimit must be positive")
    assertThatThrownBy { ScopedVfsRefreshScheduler(refreshBatchLimit = -1) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessage("refreshBatchLimit must be positive")
  }

  @Test
  fun `empty scoped refresh request is no-op`(): Unit = timeoutRunBlocking(10.seconds) {
    withSaveAndSyncHandler { handler ->
      val modificationCount = handler.getExternalChangesTracker().modificationCount

      handler.scheduleRefresh(emptyList())

      assertThat(handler.getExternalChangesTracker().modificationCount).isEqualTo(modificationCount)
    }
  }

  @Test
  fun `failing scoped refresh does not tear down scheduler`(): Unit = timeoutRunBlocking(10.seconds) {
    val firstDir = Files.createTempDirectory("scheduled-scoped-refresh-failing-first")
    val secondDir = Files.createTempDirectory("scheduled-scoped-refresh-failing-second")

    try {
      @Suppress("RAW_SCOPE_CREATION")
      val schedulerCoroutineScope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineName("SaveAndSyncHandlerScheduledRefreshTest"))
      val scheduler = ScopedVfsRefreshScheduler()
      val refreshQueue = FailingThenSucceedingRefreshQueue()
      scheduler.launchProcessing(
        coroutineScope = schedulerCoroutineScope,
        refreshQueue = refreshQueue,
        refreshGate = { ScopedVfsRefreshGate.Ready },
        beforeRefresh = {},
      )
      try {
        loadDirectory(firstDir)
        val secondRoot = loadDirectory(secondDir)

        scheduler.schedule(listOf(firstDir))
        // the first refresh throws; the failure must be swallowed and must not cancel the scheduler scope
        refreshQueue.firstRefreshFailed.await()

        // a later request still gets refreshed - proves the collector survived the failure
        scheduler.schedule(listOf(secondDir))
        assertThat(refreshQueue.secondRefreshStarted.await()).containsExactly(secondRoot)
        assertThat(schedulerCoroutineScope.coroutineContext.job.isActive).isTrue()
      }
      finally {
        schedulerCoroutineScope.coroutineContext.job.cancelAndJoin()
      }
    }
    finally {
      NioFiles.deleteRecursively(firstDir)
      NioFiles.deleteRecursively(secondDir)
    }
  }

  @Test
  fun `scheduled scoped refresh compacts nested paths`(): Unit = timeoutRunBlocking(10.seconds) {
    val requestedDir = Files.createTempDirectory("scheduled-scoped-refresh-nested")

    try {
      @Suppress("RAW_SCOPE_CREATION")
      val schedulerCoroutineScope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineName("SaveAndSyncHandlerScheduledRefreshTest"))
      val scheduler = ScopedVfsRefreshScheduler()
      val refreshQueue = RecordingRefreshQueue()
      scheduler.launchProcessing(
        coroutineScope = schedulerCoroutineScope,
        refreshQueue = refreshQueue,
        refreshGate = { ScopedVfsRefreshGate.Ready },
        beforeRefresh = {},
      )
      try {
        val nestedDir = Files.createDirectory(requestedDir.resolve("src"))
        val requestedRoot = loadDirectory(requestedDir)

        scheduler.schedule(listOf(nestedDir, requestedDir))

        waitUntil("Scoped scheduled VFS refresh did not refresh nested paths", timeout = 5.seconds) {
          refreshQueue.batches().isNotEmpty()
        }

        assertThat(refreshQueue.batches()).containsExactly(listOf(requestedRoot))
      }
      finally {
        schedulerCoroutineScope.coroutineContext.job.cancelAndJoin()
      }
    }
    finally {
      NioFiles.deleteRecursively(requestedDir)
    }
  }

  @Test
  fun `scoped refresh caps batch size and carries overflow forward`(): Unit = timeoutRunBlocking(10.seconds) {
    val dirs = (0 until 5).map { Files.createTempDirectory("scheduled-scoped-refresh-batch-$it") }

    try {
      @Suppress("RAW_SCOPE_CREATION")
      val schedulerCoroutineScope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineName("SaveAndSyncHandlerScheduledRefreshTest"))
      val scheduler = ScopedVfsRefreshScheduler(refreshBatchLimit = 2)
      val refreshQueue = RecordingRefreshQueue()
      scheduler.launchProcessing(
        coroutineScope = schedulerCoroutineScope,
        refreshQueue = refreshQueue,
        refreshGate = { ScopedVfsRefreshGate.Ready },
        beforeRefresh = {},
      )
      try {
        val roots = dirs.map { loadDirectory(it) }

        scheduler.schedule(dirs)

        waitUntil("Scoped scheduled VFS refresh did not refresh all roots", timeout = 5.seconds) {
          refreshQueue.allRefreshed().containsAll(roots)
        }

        val batches = refreshQueue.batches()
        // 5 roots, limit 2 -> three refreshes of 2, 2, 1; the overflow is carried forward, not dropped or merged into one refresh
        assertThat(batches).hasSize(3)
        assertThat(batches).allSatisfy { assertThat(it.size).isLessThanOrEqualTo(2) }
        assertThat(refreshQueue.allRefreshed()).containsExactlyInAnyOrderElementsOf(roots)
      }
      finally {
        schedulerCoroutineScope.coroutineContext.job.cancelAndJoin()
      }
    }
    finally {
      dirs.forEach { NioFiles.deleteRecursively(it) }
    }
  }

  private suspend fun CoroutineScope.withSaveAndSyncHandler(action: suspend (SaveAndSyncHandlerImpl) -> Unit) {
    @Suppress("RAW_SCOPE_CREATION")
    val handlerCoroutineScope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineName("SaveAndSyncHandlerScheduledRefreshTest"))
    val handler = SaveAndSyncHandlerImpl(handlerCoroutineScope, listenDelay = 0.seconds)
    try {
      delay(1.seconds)
      action(handler)
    }
    finally {
      handlerCoroutineScope.coroutineContext.job.cancelAndJoin()
    }
  }

  private suspend fun CoroutineScope.withScopedVfsRefreshScheduler(
    refreshQueue: BlockingRefreshQueue,
    action: suspend (ScopedVfsRefreshScheduler, BlockingRefreshQueue) -> Unit,
  ) {
    @Suppress("RAW_SCOPE_CREATION")
    val schedulerCoroutineScope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineName("SaveAndSyncHandlerScheduledRefreshTest"))
    val scheduler = ScopedVfsRefreshScheduler()
    scheduler.launchProcessing(
      coroutineScope = schedulerCoroutineScope,
      refreshQueue = refreshQueue,
      refreshGate = { ScopedVfsRefreshGate.Ready },
      beforeRefresh = {},
    )
    try {
      action(scheduler, refreshQueue)
    }
    finally {
      schedulerCoroutineScope.coroutineContext.job.cancelAndJoin()
    }
  }

  private fun loadDirectory(path: Path): VirtualFile {
    val virtualFile = VfsUtil.findFile(path, true)!!
    virtualFile.children
    VfsTestUtil.syncRefresh()
    return virtualFile
  }

  private class BlockingRefreshQueue : RefreshQueue() {
    val firstRefreshStarted = CompletableDeferred<List<VirtualFile>>()
    val secondRefreshStarted = CompletableDeferred<List<VirtualFile>>()
    val releaseFirstRefresh = CompletableDeferred<Unit>()
    private val refreshCount = AtomicInteger()

    override suspend fun refresh(recursive: Boolean, files: List<VirtualFile>) {
      check(recursive)
      when (refreshCount.incrementAndGet()) {
        1 -> {
          firstRefreshStarted.complete(files)
          releaseFirstRefresh.await()
        }
        2 -> secondRefreshStarted.complete(files)
        else -> error("Unexpected refresh")
      }
    }

    fun refreshCount(): Int = refreshCount.get()

    override fun createSession(async: Boolean, recursive: Boolean, finishRunnable: Runnable?, state: ModalityState): RefreshSession {
      throw UnsupportedOperationException()
    }

    override suspend fun refreshWithHighPriority(recursive: Boolean, files: List<VirtualFile>) {
      throw UnsupportedOperationException()
    }

    override suspend fun processEvents(events: List<VFileEvent>) {
      throw UnsupportedOperationException()
    }

    override fun processEvents(async: Boolean, events: List<VFileEvent>) {
      throw UnsupportedOperationException()
    }
  }

  private class FailingThenSucceedingRefreshQueue : RefreshQueue() {
    val firstRefreshFailed = CompletableDeferred<Unit>()
    val secondRefreshStarted = CompletableDeferred<List<VirtualFile>>()
    private val refreshCount = AtomicInteger()

    override suspend fun refresh(recursive: Boolean, files: List<VirtualFile>) {
      check(recursive)
      when (refreshCount.incrementAndGet()) {
        1 -> {
          firstRefreshFailed.complete(Unit)
          throw IOException("Simulated scoped VFS refresh failure")
        }
        2 -> secondRefreshStarted.complete(files)
        else -> error("Unexpected refresh")
      }
    }

    override fun createSession(async: Boolean, recursive: Boolean, finishRunnable: Runnable?, state: ModalityState): RefreshSession {
      throw UnsupportedOperationException()
    }

    override suspend fun refreshWithHighPriority(recursive: Boolean, files: List<VirtualFile>) {
      throw UnsupportedOperationException()
    }

    override suspend fun processEvents(events: List<VFileEvent>) {
      throw UnsupportedOperationException()
    }

    override fun processEvents(async: Boolean, events: List<VFileEvent>) {
      throw UnsupportedOperationException()
    }
  }

  private class RecordingRefreshQueue : RefreshQueue() {
    private val refreshes = CopyOnWriteArrayList<List<VirtualFile>>()

    fun batches(): List<List<VirtualFile>> = refreshes.toList()
    fun allRefreshed(): List<VirtualFile> = refreshes.flatten()

    override suspend fun refresh(recursive: Boolean, files: List<VirtualFile>) {
      check(recursive)
      refreshes.add(files)
    }

    override fun createSession(async: Boolean, recursive: Boolean, finishRunnable: Runnable?, state: ModalityState): RefreshSession {
      throw UnsupportedOperationException()
    }

    override suspend fun refreshWithHighPriority(recursive: Boolean, files: List<VirtualFile>) {
      throw UnsupportedOperationException()
    }

    override suspend fun processEvents(events: List<VFileEvent>) {
      throw UnsupportedOperationException()
    }

    override fun processEvents(async: Boolean, events: List<VFileEvent>) {
      throw UnsupportedOperationException()
    }
  }
}
