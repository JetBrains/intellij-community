// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.GeneralSettings
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.BalloonLayout
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import java.nio.file.Files
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@RegistryKey(key = "vfs.background.refresh.interval", value = "1")
internal class SaveAndSyncHandlerBackgroundRefreshTest {
  private val ideFrame = TestIdeFrame()

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
  fun `sync is not blocked and no root is dirty does not refresh`(): Unit = timeoutRunBlocking(10.seconds) {
    assertBackgroundRefresh(syncBlocked = false, dirtyRoots = false, expectRefresh = false, coroutineScope = this)
  }

  @Test
  fun `sync is not blocked and dirty roots refreshes`(): Unit = timeoutRunBlocking(10.seconds) {
    assertBackgroundRefresh(syncBlocked = false, dirtyRoots = true, expectRefresh = true, coroutineScope = this)
  }

  @Test
  fun `sync is blocked and no root is dirty does not refresh`(): Unit = timeoutRunBlocking(10.seconds) {
    assertBackgroundRefresh(syncBlocked = true, dirtyRoots = false, expectRefresh = false, coroutineScope = this)
  }

  @Test
  fun `sync is blocked and dirty roots does not refresh`(): Unit = timeoutRunBlocking(10.seconds) {
    assertBackgroundRefresh(syncBlocked = true, dirtyRoots = true, expectRefresh = false, coroutineScope = this)
  }

  private suspend fun assertBackgroundRefresh(syncBlocked: Boolean, dirtyRoots: Boolean, expectRefresh: Boolean, coroutineScope: CoroutineScope) {
    val dirtyFile = Files.createTempFile("background-refresh", ".txt")
    val virtualFile = VfsUtil.findFile(dirtyFile, true)

    @Suppress("RAW_SCOPE_CREATION")
    val handlerCoroutineScope = CoroutineScope(coroutineScope.coroutineContext + SupervisorJob() + CoroutineName("SaveAndSyncHandlerBackgroundRefreshTest"))
    val handler = SaveAndSyncHandlerImpl(handlerCoroutineScope, listenDelay = 0.seconds)

    try {
      delay(1.seconds) // wait for handler to start listening
      VfsTestUtil.syncRefresh()

      if (syncBlocked) {
        handler.blockSyncOnFrameActivation()
      }

      val virtualFileManager = VirtualFileManager.getInstance()
      val initialModificationCount = virtualFileManager.modificationCount

      deactivateFrame()

      if (dirtyRoots) {
        dirtyFile.writeText("after")
        VfsUtil.markDirty(false, false, virtualFile)
      }

      if (expectRefresh) {
        waitUntil("Background VFS refresh was not triggered", timeout = 5.seconds) {
          virtualFileManager.modificationCount > initialModificationCount
        }
      }
      else {
        delay(2500.milliseconds)
        assertThat(virtualFileManager.modificationCount).isEqualTo(initialModificationCount)
      }
    }
    finally {
      if (syncBlocked) {
        handler.unblockSyncOnFrameActivation()
      }
      dirtyFile.deleteIfExists()
      handlerCoroutineScope.coroutineContext.job.cancelAndJoin()
      activateFrame()
    }
  }

  private fun deactivateFrame() {
    ApplicationManager.getApplication().messageBus.syncPublisher(ApplicationActivationListener.TOPIC).applicationDeactivated(ideFrame)
  }

  private fun activateFrame() {
    ApplicationManager.getApplication().messageBus.syncPublisher(ApplicationActivationListener.TOPIC).applicationActivated(ideFrame)
  }
}

private class TestIdeFrame : IdeFrame {
  private val component = JPanel()

  override fun getStatusBar(): StatusBar? = null
  override fun suggestChildFrameBounds(): Rectangle = Rectangle()
  override fun getProject(): Project? = null
  override fun setFrameTitle(title: String) = Unit
  override fun getComponent(): JComponent = component
  override fun getBalloonLayout(): BalloonLayout? = null
}
