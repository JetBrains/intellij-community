// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents
import org.jetbrains.annotations.NotNull
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.io.path.invariantSeparatorsPathString

class SdkDownloaderTest : LightPlatformTestCase() {
  private val successDownloadTask = object : SdkDownloadTask {
    val home = createTempDir("planned-home").toPath().invariantSeparatorsPathString
    override fun getPlannedHomeDir() = home
    override fun getSuggestedSdkName() = "suggested name"
    override fun getPlannedVersion() = "planned version"
    override fun doDownload(indicator: ProgressIndicator) = Unit
  }

  @Test
  fun testBlockingDownloadWorks() {
    val task = object: SdkDownloadTask by successDownloadTask { }
    val sdk = newSdk("test-sdk-1")

    runInThreadAndPumpMessages {
      SdkDownloadTracker.getInstance().downloadSdk(task, listOf(sdk), ProgressIndicatorBase())
    }

    assertThat(sdk.homePath).endsWith(task.plannedHomeDir)
    assertThat(sdk.versionString).endsWith(task.plannedVersion)
  }

  @Test
  fun testBlockingDownloadWorksIfDownloadFailed() {
    val task = object: SdkDownloadTask by successDownloadTask {
      override fun doDownload(indicator: ProgressIndicator) = error("This is mock failure")
    }
    val sdk = newSdk("test-sdk-2")

    try {
      runInThreadAndPumpMessages {
        SdkDownloadTracker.getInstance().downloadSdk(task, listOf(sdk), ProgressIndicatorBase())
      }
      Assert.fail("exception is expected")
    } catch (t: Throwable) {
      assertThat(t.message).contains("This is mock failure")
    }
  }

  @Test
  fun testBlockingWhenDownloadIsRunning() {
    //ignored
    if (System.currentTimeMillis() > 0) return

    val downloadCalls = AtomicInteger(0)
    val downloadRunning = Semaphore(1)
    val downloadBlocked = Semaphore(0)
    Disposer.register(testRootDisposable, Disposable { downloadBlocked.release() })

    val task = object : SdkDownloadTask by successDownloadTask {
      override fun doDownload(indicator: ProgressIndicator) {
        if (downloadCalls.incrementAndGet() > 1) error("Only one download is allowed")
        downloadRunning.release()
        downloadBlocked.acquire()
      }
    }

    val sdk = newSdk("test-sdk-3")
    SdkDownloadTracker.getInstance().registerSdkDownload(sdk, task)

    val testThread = threadEx {
      Assert.assertTrue(downloadRunning.tryAcquire(1000, TimeUnit.MILLISECONDS))

      runInThreadAndPumpMessages {
        thread {
          Thread.sleep(200)
          downloadBlocked.release()
        }

        SdkDownloadTracker.getInstance().downloadSdk(task, listOf(sdk), ProgressIndicatorBase())
      }
    }

    //background progress is blocking in the test mode
    SdkDownloadTracker.getInstance().startSdkDownloadIfNeeded(sdk)

    testThread.join()
  }

  private fun newSdk(sdkName: String): @NotNull Sdk {
    val sdk = ProjectJdkTable.getInstance().createSdk(sdkName, SimpleJavaSdkType.getInstance())
    if (sdk is Disposable) {
      Disposer.register(testRootDisposable, sdk)
    }
    return sdk
  }

  private fun <R> runInThreadAndPumpMessages(action: () -> R) : R {
    val result = AtomicReference<Result<R>>(null)
    val th = threadEx { result.set(runCatching { action() }) }
    while (th.isAlive) {
      ProgressManager.checkCanceled()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      th.join(100)
    }
    return result.get()?.getOrThrow() ?: error("No result was set")
  }

  private fun threadEx(task: () -> Unit): Thread {
    val thread = thread(block = task)
    Disposer.register(testRootDisposable, Disposable { thread.interrupt(); thread.join(500) })
    return thread
  }
}
