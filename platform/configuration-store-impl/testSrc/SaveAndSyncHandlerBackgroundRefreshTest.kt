// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdleTracker
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@RegistryKey(key = "vfs.background.refresh.interval", value = "1")
@RegistryKey(key = "vfs.background.refresh.on.idle", value = "true")
internal class SaveAndSyncHandlerBackgroundRefreshTest {
  @BeforeEach
  fun `set settings`() {
    GeneralSettings.getInstance().apply {
      isBackgroundSync = true
      isSyncOnFrameActivation = false
      isSaveOnFrameDeactivation = false
      isAutoSaveIfInactive = false
    }
  }

  @Test
  fun `idle refresh with dirty roots refreshes while frame stays active`(): Unit = timeoutRunBlocking(10.seconds) {
    assertBackgroundRefresh(expectedRefreshCount = 1)
  }

  @Test
  fun `idle refresh can happen 3 times while user stays inactive`(): Unit = timeoutRunBlocking(15.seconds) {
    assertBackgroundRefresh(expectedRefreshCount = 3)
  }

  @Test
  fun `user activity prevents background vfs refresh`(): Unit = timeoutRunBlocking(10.seconds) {
    val simulatedUserActivityJob = launch(CoroutineName("simulated user activity")) {
      val idleTracker = serviceAsync<IdleTracker>()
      while (true) {
        delay(100.milliseconds)
        idleTracker.restartIdleTimer()
      }
    }

    assertBackgroundRefresh(expectedRefreshCount = 0)

    simulatedUserActivityJob.cancelAndJoin()
  }

  @Test
  fun `idle refresh is disabled by general settings`(): Unit = timeoutRunBlocking(10.seconds) {
    GeneralSettings.getInstance().isBackgroundSync = false
    assertBackgroundRefresh(expectedRefreshCount = 0)
  }

  private suspend fun CoroutineScope.assertBackgroundRefresh(expectedRefreshCount: Int) {
    val dirtyFile = Files.createTempFile("background-refresh", ".txt")
    val virtualFile = VfsUtil.findFile(dirtyFile, true)

    @Suppress("RAW_SCOPE_CREATION")
    val handlerCoroutineScope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineName("SaveAndSyncHandlerBackgroundRefreshTest"))
    SaveAndSyncHandlerImpl(handlerCoroutineScope, listenDelay = 0.seconds)

    try {
      delay(1.seconds) // wait for handler to start listening
      VfsTestUtil.syncRefresh()
      serviceAsync<IdleTracker>().restartIdleTimer()

      val virtualFileManager = VirtualFileManager.getInstance()
      var modificationCountBaseline = virtualFileManager.modificationCount

      if (expectedRefreshCount > 0) {
        repeat(expectedRefreshCount) { refreshIndex ->
          dirtyFile.writeText("after-$refreshIndex")
          VfsUtil.markDirty(false, false, virtualFile)

          waitUntil("Background VFS refresh ${refreshIndex + 1} was not triggered", timeout = 5.seconds) {
            virtualFileManager.modificationCount > modificationCountBaseline
          }

          modificationCountBaseline = virtualFileManager.modificationCount
        }
      }
      else {
        dirtyFile.writeText("after")
        VfsUtil.markDirty(false, false, virtualFile)

        delay(3.seconds)
        Assertions.assertThat(virtualFileManager.modificationCount).isEqualTo(modificationCountBaseline)
      }
    }
    finally {
      dirtyFile.deleteIfExists()
      handlerCoroutineScope.coroutineContext.job.cancelAndJoin()
    }
  }
}
