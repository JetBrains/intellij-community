// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.ui.UIUtil.*
import org.jetbrains.annotations.NotNull
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class SdkDownloaderTest : LightPlatformTestCase() {
  private val successDownloadTask = object: SdkDownloadTask {
    val home = createTempDir("planned-home").toPath().systemIndependentPath
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

  private fun newSdk(sdkName: String): @NotNull Sdk {
    val sdk = ProjectJdkTable.getInstance().createSdk(sdkName, SimpleJavaSdkType.getInstance())
    if (sdk is Disposable) {
      Disposer.register(testRootDisposable, sdk)
    }
    return sdk
  }

  private fun <R> runInThreadAndPumpMessages(action: () -> R) : R {
    val result = AtomicReference<Result<R>>(null)
    val th = thread { result.set(runCatching { action() }) }
    while (th.isAlive) {
      ProgressManager.checkCanceled()
      dispatchAllInvocationEvents()
      th.join(100)
    }
    return result.get()?.getOrThrow() ?: error("No result was set")
  }
}
