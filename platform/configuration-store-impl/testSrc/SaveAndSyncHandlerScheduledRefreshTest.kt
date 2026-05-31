// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.GeneralSettings
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
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
      withSaveAndSyncHandler { handler ->
        val firstRoot = loadDirectory(firstDir)
        val secondRoot = loadDirectory(secondDir)

        firstDir.resolve("first.txt").writeText("first")
        secondDir.resolve("second.txt").writeText("second")
        VfsUtil.markDirty(false, false, firstRoot, secondRoot)

        handler.scheduleRefresh(listOf(firstDir))
        handler.scheduleRefresh(listOf(secondDir))

        waitUntil("Scoped scheduled VFS refresh did not refresh coalesced roots", timeout = 5.seconds) {
          firstRoot.findChild("first.txt") != null && secondRoot.findChild("second.txt") != null
        }
      }
    }
    finally {
      NioFiles.deleteRecursively(firstDir)
      NioFiles.deleteRecursively(secondDir)
    }
  }

  @Test
  fun `empty scoped refresh request is no-op`(): Unit = timeoutRunBlocking(10.seconds) {
    withSaveAndSyncHandler { handler ->
      val modificationCount = handler.getExternalChangesTracker().modificationCount

      handler.scheduleRefresh(emptyList())

      assertThat(handler.getExternalChangesTracker().modificationCount).isEqualTo(modificationCount)
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

  private fun loadDirectory(path: Path): VirtualFile {
    val virtualFile = VfsUtil.findFile(path, true)!!
    virtualFile.children
    VfsTestUtil.syncRefresh()
    return virtualFile
  }
}
