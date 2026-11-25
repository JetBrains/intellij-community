// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.WriteIntentReadActionListener
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.useBackgroundWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class ProjectRootManagerTest {
  private val projectFixture = projectFixture()

  @Test
  fun testDisposedModule(): Unit = timeoutRunBlocking {
    val module = edtWriteAction { ModuleManager.getInstance(projectFixture.get()).newNonPersistentModule("someModule", "someId") }
    edtWriteAction {
      ModuleManager.getInstance(projectFixture.get()).disposeModule(module)
    }
    readAction {
      Assertions.assertThrows(AlreadyDisposedException::class.java, {
        ModuleRootManager.getInstance(module)
      }, "Manager should throw exception if module is disposed")
    }
  }

  @Test
  fun `a waiting batch finish preserves changes under write intent`(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking(timeout = 30.seconds) {
      assumeTrue(useBackgroundWriteAction)
      val events = mutableListOf<List<RootsChangeRescanningInfo>>()
      val manager = object : ProjectRootManagerImpl(projectFixture.get(), this@timeoutRunBlocking) {
        override fun clearScopesCaches() {}

        override fun fireRootsChangedEvent(fileTypes: Boolean, indexingInfos: List<RootsChangeRescanningInfo>) {
          events.add(indexingInfos)
        }
      }
      val application = ApplicationManager.getApplication()
      val attemptingWrite = CountDownLatch(1)
      val task = FutureTask { manager.rootsChanged.levelDown() }
      val worker = Thread(task, "roots batch finish worker").apply { isDaemon = true }
      ApplicationManagerEx.getApplicationEx().addWriteActionListener(object : WriteActionListener {
        override fun beforeWriteActionStart(action: Class<*>) {
          if (Thread.currentThread() === worker) attemptingWrite.countDown()
        }
      }, disposable)
      ApplicationManagerEx.getApplicationEx().addWriteIntentReadActionListener(object : WriteIntentReadActionListener {
        override fun beforeWriteIntentReadActionStart(action: Class<*>) {
          if (Thread.currentThread() === worker) attemptingWrite.countDown()
        }
      }, disposable)
      var started = false
      try {
        withContext(Dispatchers.IO) {
          WriteIntentReadAction.run {
            manager.rootsChanged.levelUp()
            application.runWriteAction {
              manager.rootsChanged.beforeRootsChanged()
              manager.rootsChanged.rootsChanged(RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
            }
            worker.start()
            started = true
            Assertions.assertTrue(attemptingWrite.await(10, TimeUnit.SECONDS))
            application.runWriteAction {
              manager.rootsChanged.beforeRootsChanged()
              manager.rootsChanged.rootsChanged(RootsChangeRescanningInfo.TOTAL_RESCAN)
            }
            Assertions.assertTrue(events.isEmpty(), "The batch must remain open until its finish acquires write intent")
          }
        }
        withContext(Dispatchers.IO) { task.get(10, TimeUnit.SECONDS) }
        Assertions.assertEquals(listOf(listOf(RootsChangeRescanningInfo.NO_RESCAN_NEEDED, RootsChangeRescanningInfo.TOTAL_RESCAN)), events)
      }
      finally {
        if (started) {
          withContext(Dispatchers.IO) { worker.join(10_000) }
          Assertions.assertFalse(worker.isAlive)
        }
      }
    }
}
