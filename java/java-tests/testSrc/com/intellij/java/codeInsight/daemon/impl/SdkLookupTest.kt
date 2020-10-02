// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.ui.configuration.*
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.setSystemPropertyForTest
import com.intellij.util.WaitFor
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.ui.UIUtil
import org.junit.Assert
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class SdkLookupTest : LightPlatformTestCase() {
  override fun setUp() {
    super.setUp()
    setSystemPropertyForTest("intellij.progress.task.ignoreHeadless", "true")
  }

  val log = Collections.synchronizedList(mutableListOf<String>())
  val sdkType get() = SimpleJavaSdkType.getInstance()!!

  val lookup get() = SdkLookup
    .newLookupBuilder()
    .withProject(project)
    .withProgressIndicator(ProgressIndicatorBase())
    .withSdkType(sdkType)
    .onSdkNameResolved {  log += "sdk-name: ${it?.name}" }
    .onSdkResolved { log += "sdk: ${it?.name}" }

  fun SdkLookupBuilder.lookupBlocking() = service<SdkLookup>().lookupBlocking(this as SdkLookupParameters)

  private fun assertLog(vararg messages: String) {
    fun List<String>.format() = joinToString("") { "\n  $it" }
    Assert.assertEquals("actual log: " + log.format(), messages.toList().format(), log.format())
  }

  fun `test no sdk found`() {
    runInThreadAndPumpMessages {
      lookup.lookupBlocking()
    }
    assertLog(
      "sdk-name: null",
      "sdk: null",
    )
  }

  fun `test find existing by name`() {
    val sdk = newSdk("temp-1")
    runInThreadAndPumpMessages {
      lookup.withSdkName(sdk.name).lookupBlocking()
    }
    assertLog(
      "sdk-name: temp-1",
      "sdk: temp-1",
    )
  }

  fun `test find sdk from alternatives`() {
    val sdk1 = newUnregisteredSdk("temp-3")
    val sdk2 = newUnregisteredSdk("temp-2")
    runInThreadAndPumpMessages {
      lookup
        .testSuggestedSdksFirst(sequenceOf(null, sdk1, sdk2))
        .lookupBlocking()
    }
    assertLog(
      "sdk-name: temp-3",
      "sdk: temp-3",
    )
  }

  fun `test find sdk from alternatives and filter`() {
    val sdk1 = newUnregisteredSdk("temp-3", "1.2.3")
    val sdk2 = newUnregisteredSdk("temp-2", "2.3.4")

    runInThreadAndPumpMessages {
      lookup
        .testSuggestedSdksFirst(sequenceOf(null, sdk1, sdk2))
        .withVersionFilter { it == "2.3.4" }
        .lookupBlocking()
    }
    assertLog(
      "sdk-name: temp-2",
      "sdk: temp-2",
    )
  }

  fun `test find downloading sdk`() {
    val taskLatch = CountDownLatch(1)
    val downloadStarted = CountDownLatch(1)
    Disposer.register(testRootDisposable, Disposable { taskLatch.countDown() })
    val eternalTask = object: SdkDownloadTask {
      val home = createTempDir("planned-home").toPath().systemIndependentPath
      override fun getPlannedHomeDir() = home
      override fun getSuggestedSdkName() = "suggested name"
      override fun getPlannedVersion() = "planned version"
      override fun doDownload(indicator: ProgressIndicator) {
        downloadStarted.countDown()
        taskLatch.await()
        log += "download-completed"
      }
    }

    val sdk = newSdk("temp-5")
    threadEx {
      SdkDownloadTracker.getInstance().downloadSdk(eternalTask, listOf(sdk), ProgressIndicatorBase())
    }

    runInThreadAndPumpMessages {
      downloadStarted.await()
    }

    //download should be running now

    threadEx {
      object: WaitFor(1000) {
        //this event should come from the lookup
        override fun condition() = log.any { it.startsWith("sdk-name:") }
      }

      log += "thread-ex"
      taskLatch.countDown()
    }

    runInThreadAndPumpMessages {
      //right now it hangs doing async VFS refresh in downloader thread if running from a modal progress.
      //ProgressManager.getInstance().run(object : Task.Modal(project, "sad", true) {
      //  override fun run(indicator: ProgressIndicator) {
          lookup
            .withSdkName("temp-5")
            .lookupBlocking()
        //}
      //})
    }

    assertLog(
      "sdk-name: temp-5",
      "thread-ex",
      "download-completed",
      "sdk: temp-5",
    )
  }

  fun `test find downloading sdk async`() {
    val taskLatch = CountDownLatch(1)
    val downloadStarted = CountDownLatch(1)
    Disposer.register(testRootDisposable, Disposable { taskLatch.countDown() })
    val eternalTask = object: SdkDownloadTask {
      val home = createTempDir("planned-home").toPath().systemIndependentPath
      override fun getPlannedHomeDir() = home
      override fun getSuggestedSdkName() = "suggested name"
      override fun getPlannedVersion() = "planned version"
      override fun doDownload(indicator: ProgressIndicator) {
        downloadStarted.countDown()
        taskLatch.await()
        log += "download-completed"
      }
    }

    val sdk = newSdk("temp-5")
    threadEx {
      SdkDownloadTracker.getInstance().downloadSdk(eternalTask, listOf(sdk), ProgressIndicatorBase())
    }

    runInThreadAndPumpMessages {
      downloadStarted.await()
    }

    //download should be running now

    threadEx {
      object: WaitFor(1000) {
        //this event should come from the lookup
        override fun condition() = log.any { it.startsWith("sdk-name:") }
      }

      log += "thread-ex"
      taskLatch.countDown()
    }

    val lookupLatch = CountDownLatch(1)
    lookup
      .withSdkName("temp-5")
      .onSdkResolved { lookupLatch.countDown() }
      .executeLookup()

    runInThreadAndPumpMessages {
      lookupLatch.await()
    }

    assertLog(
      "sdk-name: temp-5",
      "thread-ex",
      "download-completed",
      "sdk: temp-5",
    )
  }

  fun `test local fix`() {
    val auto = object: UnknownSdkResolver {
      override fun supportsResolution(sdkTypeId: SdkTypeId) = sdkTypeId == sdkType
      override fun createResolver(project: Project?, indicator: ProgressIndicator) = object : UnknownSdkResolver.UnknownSdkLookup {
        override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkDownloadableSdkFix? = null
        override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkLocalSdkFix? {
          if (sdk.sdkName != "xqwr") return null
          return object : UnknownSdkLocalSdkFix {
            val home = createTempDir("our home for ${sdk.sdkName}")
            override fun configureSdk(sdk: Sdk)  { log += "configure: ${sdk.name}"}
            override fun getExistingSdkHome() = home.toString()
            override fun getVersionString() = "1.2.3"
            override fun getSuggestedSdkName() = sdk.sdkName!!
          }
        }
      }
    }
    ExtensionTestUtil.maskExtensions(UnknownSdkResolver.EP_NAME, listOf(auto), testRootDisposable)

    runInThreadAndPumpMessages {
      lookup
        .withSdkName("xqwr")
        .lookupBlocking()
    }

    assertLog(
      "configure: xqwr",
      "sdk-name: xqwr",
      "sdk: xqwr",
    )
  }

  fun `test local fix with SDK prototype`() {
    val prototypeSdk = newSdk("prototype")
    val auto = object : UnknownSdkResolver {
      override fun supportsResolution(sdkTypeId: SdkTypeId) = sdkTypeId == sdkType
      override fun createResolver(project: Project?, indicator: ProgressIndicator) = object : UnknownSdkResolver.UnknownSdkLookup {
        override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkDownloadableSdkFix? = null
        override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator) = object : UnknownSdkLocalSdkFix {
          val home = createTempDir("our home for ${sdk.sdkName}")
          override fun configureSdk(sdk: Sdk) { log += "configure: ${sdk.name}" }
          override fun getExistingSdkHome() = home.toString()
          override fun getVersionString() = "1.2.3"
          override fun getSuggestedSdkName() = sdk.sdkName!!
          override fun getRegisteredSdkPrototype() = prototypeSdk
        }
      }
    }
    ExtensionTestUtil.maskExtensions(UnknownSdkResolver.EP_NAME, listOf(auto), testRootDisposable)

    runInThreadAndPumpMessages {
      lookup
        .lookupBlocking()
    }

    assertLog(
      "sdk-name: prototype",
      "sdk: prototype",
    )
  }

  fun `test local fix should not clash with SDK name`() {
    val prototypeSdk = newSdk("prototype")
    val auto = object : UnknownSdkResolver {
      override fun supportsResolution(sdkTypeId: SdkTypeId) = sdkTypeId == sdkType
      override fun createResolver(project: Project?, indicator: ProgressIndicator) = object : UnknownSdkResolver.UnknownSdkLookup {
        override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkDownloadableSdkFix? = null
        override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator) = object : UnknownSdkLocalSdkFix {
          val home = createTempDir("our home for ${sdk.sdkName}")
          override fun configureSdk(sdk: Sdk) { log += "configure: ${sdk.name}" }
          override fun getExistingSdkHome() = home.toString()
          override fun getVersionString() = "1.2.3"
          override fun getSuggestedSdkName() = prototypeSdk.name
        }
      }
    }
    ExtensionTestUtil.maskExtensions(UnknownSdkResolver.EP_NAME, listOf(auto), testRootDisposable)

    runInThreadAndPumpMessages {
      lookup
        .lookupBlocking()
    }

    assertLog(
      "configure: prototype (2)",
      "sdk-name: prototype (2)",
      "sdk: prototype (2)",
    )
  }

  fun `test download fix`() {
    val auto = object: UnknownSdkResolver {
      override fun supportsResolution(sdkTypeId: SdkTypeId) = sdkTypeId == sdkType
      override fun createResolver(project: Project?, indicator: ProgressIndicator) = object : UnknownSdkResolver.UnknownSdkLookup {
        override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkLocalSdkFix? =  null
        override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkDownloadableSdkFix? {
          if (sdk.sdkName != "xqwr") return null
          return object : UnknownSdkDownloadableSdkFix {
            override fun getDownloadDescription(): String = "download description"
            override fun createTask(indicator: ProgressIndicator) = object: SdkDownloadTask {
              override fun getSuggestedSdkName() = sdk.sdkName!!
              override fun getPlannedHomeDir() = home.toString()
              override fun getPlannedVersion() = versionString
              override fun doDownload(indicator: ProgressIndicator) { log += "download: ${sdk.sdkName}" }
            }
            val home = createTempDir("our home for ${sdk.sdkName}")
            override fun configureSdk(sdk: Sdk)  { log += "configure: ${sdk.name}"}
            override fun getVersionString() = "1.2.3"
          }
        }
      }
    }
    ExtensionTestUtil.maskExtensions(UnknownSdkResolver.EP_NAME, listOf(auto), testRootDisposable)

    runInThreadAndPumpMessages {
      lookup
        .withSdkName("xqwr")
        .lookupBlocking()
    }

    assertLog(
      "sdk-name: xqwr",
      "download: xqwr",
      "configure: xqwr",
      "sdk: xqwr",
    )
  }

  fun `test download fix should not clash SDK name`() {
    val prototypeSdk = newSdk("prototype")
    val auto = object: UnknownSdkResolver {
      override fun supportsResolution(sdkTypeId: SdkTypeId) = sdkTypeId == sdkType
      override fun createResolver(project: Project?, indicator: ProgressIndicator) = object : UnknownSdkResolver.UnknownSdkLookup {
        override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkLocalSdkFix? =  null
        override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator) = object : UnknownSdkDownloadableSdkFix {
            override fun getDownloadDescription(): String = "download description"
            override fun createTask(indicator: ProgressIndicator) = object: SdkDownloadTask {
              override fun getSuggestedSdkName() = prototypeSdk.name
              override fun getPlannedHomeDir() = home.toString()
              override fun getPlannedVersion() = versionString
              override fun doDownload(indicator: ProgressIndicator) { log += "download: ${sdk.sdkName}" }
            }
            val home = createTempDir("our home for ${sdk.sdkName}")
            override fun configureSdk(sdk: Sdk)  { log += "configure: ${sdk.name}"}
            override fun getVersionString() = "1.2.3"
        }
      }
    }
    ExtensionTestUtil.maskExtensions(UnknownSdkResolver.EP_NAME, listOf(auto), testRootDisposable)

    runInThreadAndPumpMessages {
      lookup
        .lookupBlocking()
    }

    assertLog(
      "sdk-name: prototype (2)",
      "download: null",
      "configure: prototype (2)",
      "sdk: prototype (2)",
    )
  }

  private fun newSdk(sdkName: String, version: String = "1.2.3"): Sdk {
    return WriteAction.compute<Sdk, Throwable> {
      val sdk = newUnregisteredSdk(sdkName, version)
      ProjectJdkTable.getInstance().addJdk(sdk, testRootDisposable)
      sdk
    }
  }

  private fun newUnregisteredSdk(sdkName: String,
                                 version: String = "1.2.3"): Sdk {
    val sdk = ProjectJdkTable.getInstance().createSdk(sdkName, sdkType)
    sdk.sdkModificator.also { it.versionString = version }.commitChanges()
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
      UIUtil.dispatchAllInvocationEvents()
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
