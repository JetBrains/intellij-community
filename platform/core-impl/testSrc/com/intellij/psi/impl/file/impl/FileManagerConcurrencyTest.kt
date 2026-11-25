// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.WriteIntentReadActionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.useBackgroundWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class FileManagerConcurrencyTest {
  private val projectFixture: TestFixture<Project> = projectFixture()

  @Test
  fun `a waiting file type change does not suppress the current write action`(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking(timeout = 30.seconds) {
      assumeTrue(useBackgroundWriteAction)
      val manager = PsiManagerEx.getInstanceEx(projectFixture.get())
      val fileManager = manager.fileManagerEx
      val changes = AtomicInteger()
      manager.addPsiTreeChangeListenerBackgroundable(object : PsiTreeChangeAdapter() {
        override fun beforePropertyChange(event: PsiTreeChangeEvent) {
          if (event.propertyName == PsiTreeChangeEvent.PROP_FILE_TYPES) {
            changes.incrementAndGet()
            fileManager.processFileTypesChanged(false)
          }
        }
      }, disposable)
      val attemptingWrite = CountDownLatch(1)
      val task = FutureTask { fileManager.processFileTypesChanged(false) }
      val worker = Thread(task, "file type change worker").apply { isDaemon = true }
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
            worker.start()
            started = true
            assertTrue(attemptingWrite.await(10, TimeUnit.SECONDS))
            fileManager.processFileTypesChanged(true)
            assertEquals(1, changes.get())
          }
        }
        withContext(Dispatchers.IO) { task.get(10, TimeUnit.SECONDS) }
        assertEquals(2, changes.get())
      }
      finally {
        if (started) {
          withContext(Dispatchers.IO) { worker.join(10_000) }
          assertFalse(worker.isAlive)
        }
      }
    }
}
