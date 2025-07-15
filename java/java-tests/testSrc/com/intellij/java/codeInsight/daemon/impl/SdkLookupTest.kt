// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.projectRoots.impl.UnknownSdkFixAction
import com.intellij.openapi.roots.ui.configuration.*
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.SystemPropertyRule
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.WaitFor
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.io.path.invariantSeparatorsPathString

@RunsInEdt
class SdkLookupTest : BareTestFixtureTestCase() {
  @Rule @JvmField val tempDir = TempDirectory()
  @Rule @JvmField val runInEdt = EdtRule()
  @Rule @JvmField val testProgress = SystemPropertyRule("intellij.progress.task.ignoreHeadless", "true")

  val log: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())
  val sdkType get() = SimpleJavaSdkType.getInstance()!!

  interface SdkLookupBuilderEx : SdkLookupBuilder {
    fun onDownloadingSdkDetectedEx(d: SdkLookupDownloadDecision): SdkLookupBuilderEx
    fun onSdkFixResolved(d: SdkLookupDecision): SdkLookupBuilderEx
    fun lookupBlocking()
  }

  private val lookup: SdkLookupBuilderEx
    get() {
      var ourFixDecision = SdkLookupDecision.CONTINUE
      var ourSdkDownloadDecision = SdkLookupDownloadDecision.WAIT
      var onSdkResolvedHook : (Sdk?) -> Unit = {}

      val base = SdkLookup.newLookupBuilder()
        .withProgressIndicator(ProgressIndicatorBase())
        .withSdkType(sdkType)
        .onSdkNameResolved { log += "sdk-name: ${it?.name}" }
        .onSdkResolved { onSdkResolvedHook(it); log += "sdk: ${it?.name}" }
        .onDownloadingSdkDetected { log += "sdk-downloading: ${it.name}"; ourSdkDownloadDecision }
        .onSdkFixResolved { log += "fix: ${it.javaClass.simpleName}"; ourFixDecision }

      return object : SdkLookupBuilder by base, SdkLookupBuilderEx {
        override fun lookupBlocking() = base.lookupBlocking()

        override fun onDownloadingSdkDetectedEx(d: SdkLookupDownloadDecision) = apply {
          ourSdkDownloadDecision = d
        }

        override fun onSdkFixResolved(d: SdkLookupDecision) = apply {
          ourFixDecision = d
        }

        override fun onDownloadingSdkDetected(handler: (Sdk) -> SdkLookupDownloadDecision): SdkLookupBuilder = error("Must not call in test")
        override fun onSdkFixResolved(handler: (UnknownSdkFixAction) -> SdkLookupDecision): SdkLookupBuilder  = error("Must not call in test")
        override fun onSdkNameResolved(handler: (Sdk?) -> Unit): SdkLookupBuilder = error("Must not call in test")

        override fun onSdkResolved(handler: (Sdk?) -> Unit): SdkLookupBuilder = apply {
          onSdkResolvedHook = handler
        }
      }
    }

  fun SdkLookupBuilder.lookupBlocking(): Unit = service<SdkLookup>().lookupBlocking(this as SdkLookupParameters)

  private fun assertLog(vararg messages: String) {
    assertThat(log).containsExactly(*messages)
  }

  @Test fun `test no sdk found`() {
    runInThreadAndPumpMessages {
      lookup.lookupBlocking()
    }
    assertLog(
      "sdk-name: null",
      "sdk: null",
    )
  }

  @Test fun `test find existing by name`() {
    val sdk = newSdk("temp-1")
    runInThreadAndPumpMessages {
      lookup.withSdkName(sdk.name).lookupBlocking()
    }
    assertLog(
      "sdk-name: temp-1",
      "sdk: temp-1",
    )
  }

  @Test fun `test find sdk from alternatives`() {
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

  @Test fun `test find sdk from alternatives and filter`() {
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

  @Test fun `test find downloading sdk`() {
    val taskLatch = CountDownLatch(1)
    val downloadStarted = CountDownLatch(1)
    Disposer.register(testRootDisposable, Disposable { taskLatch.countDown() })
    val eternalTask = object : SdkDownloadTask {
      val home = tempDir.newDirectoryPath("planned-home").invariantSeparatorsPathString
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
      //ProgressManager.getInstance().run(object : Task.Modal(project, "temp", true) {
      //  override fun run(indicator: ProgressIndicator) {
          lookup
            .withSdkName("temp-5")
            .lookupBlocking()
        //}
      //})
    }

    assertLog(
      "sdk-name: temp-5",
      "sdk-downloading: temp-5",
      "thread-ex",
      "download-completed",
      "sdk: temp-5",
    )
  }

  @Test fun `test find downloading sdk stop`() {
    val taskLatch = CountDownLatch(1)
    val downloadStarted = CountDownLatch(1)
    Disposer.register(testRootDisposable, Disposable { taskLatch.countDown() })
    val eternalTask = object : SdkDownloadTask {
      val home = tempDir.newDirectoryPath("planned-home").invariantSeparatorsPathString
      override fun getPlannedHomeDir() = home
      override fun getSuggestedSdkName() = "suggested name"
      override fun getPlannedVersion() = "planned version"
      override fun doDownload(indicator: ProgressIndicator) {
        downloadStarted.countDown()
        log += "download-started"
        taskLatch.await()
        log += "download-completed"
      }
    }

    val sdk = newSdk("temp-5")
    val download = threadEx {
      SdkDownloadTracker.getInstance().downloadSdk(eternalTask, listOf(sdk), ProgressIndicatorBase())
    }

    runInThreadAndPumpMessages {
      downloadStarted.await()
    }

    //download should be running now

    val downloadThread = threadEx {
      object : WaitFor(1000) {
        //this event should come from the lookup
        override fun condition() = log.any { it.startsWith("sdk-downloading:") }
      }

      log += "thread-ex"
      taskLatch.countDown()
      download.join()
    }

    runInThreadAndPumpMessages {
      lookup
        .onDownloadingSdkDetectedEx(SdkLookupDownloadDecision.STOP)
        .onSdkResolved {
          downloadThread.join()
        }
        .withSdkName("temp-5")
        .lookupBlocking()

      downloadThread.join()
    }

    assertLog(
      "download-started",
      "sdk-name: temp-5",
      "sdk-downloading: temp-5",
      "thread-ex",
      "download-completed",
      "sdk: null",
      )
  }

  @Test fun `test find downloading sdk async`() {
    val taskLatch = CountDownLatch(1)
    val downloadStarted = CountDownLatch(1)
    Disposer.register(testRootDisposable, Disposable { taskLatch.countDown() })
    val eternalTask = object : SdkDownloadTask {
      val home = tempDir.newDirectoryPath("planned-home").invariantSeparatorsPathString
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
      "sdk-downloading: temp-5",
      "thread-ex",
      "download-completed",
      "sdk: temp-5",
    )
  }

  @Test fun `test local fix`() {
    val auto = object: UnknownSdkResolver {
      override fun supportsResolution(sdkTypeId: SdkTypeId) = sdkTypeId == sdkType
      override fun createResolver(project: Project?, indicator: ProgressIndicator) = object : UnknownSdkResolver.UnknownSdkLookup {
        override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkDownloadableSdkFix? = null
        override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkLocalSdkFix? {
          if (sdk.sdkName != "xqwr") return null
          return object : UnknownSdkLocalSdkFix {
            val home = tempDir.newDirectoryPath("our home for ${sdk.sdkName}")
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
      "fix: UnknownMissingSdkFixLocal",
      "configure: xqwr",
      "sdk-name: xqwr",
      "sdk: xqwr",
    )
    removeSdk("xqwr")
  }

  private fun removeSdk(name: String) {
    val sdk = ProjectJdkTable.getInstance().findJdk(name) ?: error("SDK '$name' doesn't exist")
    SdkTestCase.removeSdk(sdk)
  }

  @Test fun `test local fix with SDK prototype`() {
    val prototypeSdk = newSdk("prototype")
    val auto = object : UnknownSdkResolver {
      override fun supportsResolution(sdkTypeId: SdkTypeId) = sdkTypeId == sdkType
      override fun createResolver(project: Project?, indicator: ProgressIndicator) = object : UnknownSdkResolver.UnknownSdkLookup {
        override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkDownloadableSdkFix? = null
        override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator) = object : UnknownSdkLocalSdkFix {
          val home = tempDir.newDirectoryPath("our home for ${sdk.sdkName}")
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

  @Test fun `test local fix with unregistered SDK prototype`() {
    val prototypeSdk = newUnregisteredSdk("prototype")
    val auto = object : UnknownSdkResolver {
      override fun supportsResolution(sdkTypeId: SdkTypeId) = sdkTypeId == sdkType
      override fun createResolver(project: Project?, indicator: ProgressIndicator) = object : UnknownSdkResolver.UnknownSdkLookup {
        override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkDownloadableSdkFix? = null
        override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator) = object : UnknownSdkLocalSdkFix {
          val home = tempDir.newDirectoryPath("our home for ${sdk.sdkName}")
          override fun configureSdk(sdk: Sdk) { log += "configure: ${sdk.name}" }
          override fun getExistingSdkHome() = home.toString()
          override fun getVersionString() = "1.2.3"
          override fun getSuggestedSdkName() = "suggested-name"
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
      "fix: UnknownMissingSdkFixLocal",
      "configure: suggested-name",
      "sdk-name: suggested-name",
      "sdk: suggested-name",
    )
    removeSdk("suggested-name")
  }


  @Test fun `test local fix with stop`() {
    val prototypeSdk = newUnregisteredSdk("prototype")
    val auto = object : UnknownSdkResolver {
      override fun supportsResolution(sdkTypeId: SdkTypeId) = sdkTypeId == sdkType
      override fun createResolver(project: Project?, indicator: ProgressIndicator) = object : UnknownSdkResolver.UnknownSdkLookup {
        override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkDownloadableSdkFix? = null
        override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator) = object : UnknownSdkLocalSdkFix {
          val home = tempDir.newDirectoryPath("our home for ${sdk.sdkName}")
          override fun configureSdk(sdk: Sdk) { log += "configure: ${sdk.name}" }
          override fun getExistingSdkHome() = home.toString()
          override fun getVersionString() = "1.2.3"
          override fun getSuggestedSdkName() = "suggested-name"
          override fun getRegisteredSdkPrototype() = prototypeSdk
        }
      }
    }
    ExtensionTestUtil.maskExtensions(UnknownSdkResolver.EP_NAME, listOf(auto), testRootDisposable)

    runInThreadAndPumpMessages {
      lookup
        .onSdkFixResolved(SdkLookupDecision.STOP)
        .lookupBlocking()
    }

    assertLog(
      "fix: UnknownMissingSdkFixLocal",
      "sdk-name: null",
      "sdk: null",
    )
  }

  @Test fun `test local fix should not clash with SDK name`() {
    val prototypeSdk = newSdk("prototype")
    val auto = object : UnknownSdkResolver {
      override fun supportsResolution(sdkTypeId: SdkTypeId) = sdkTypeId == sdkType
      override fun createResolver(project: Project?, indicator: ProgressIndicator) = object : UnknownSdkResolver.UnknownSdkLookup {
        override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkDownloadableSdkFix? = null
        override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator) = object : UnknownSdkLocalSdkFix {
          val home = tempDir.newDirectoryPath("our home for ${sdk.sdkName}")
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
      "fix: UnknownMissingSdkFixLocal",
      "configure: prototype (2)",
      "sdk-name: prototype (2)",
      "sdk: prototype (2)",
    )
  }

  @Test fun `test download fix`() {
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
            val home = tempDir.newDirectoryPath("our home for ${sdk.sdkName}")
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
      "fix: UnknownMissingSdkFixDownload",
      "sdk-name: xqwr",
      "download: xqwr",
      "configure: xqwr",
      "sdk: xqwr",
    )
    removeSdk("xqwr")
  }

  @Test fun `test download fix stop`() {
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
            val home = tempDir.newDirectoryPath("our home for ${sdk.sdkName}")
            override fun configureSdk(sdk: Sdk)  { log += "configure: ${sdk.name}"}
            override fun getVersionString() = "1.2.3"
          }
        }
      }
    }
    ExtensionTestUtil.maskExtensions(UnknownSdkResolver.EP_NAME, listOf(auto), testRootDisposable)

    runInThreadAndPumpMessages {
      lookup
        .onSdkFixResolved(SdkLookupDecision.STOP)
        .withSdkName("xqwr")
        .lookupBlocking()
    }

    assertLog(
      "fix: UnknownMissingSdkFixDownload",
      "sdk-name: null",
      "sdk: null",
    )
  }

  @Test fun `test download fix should not clash SDK name`() {
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
            val home = tempDir.newDirectoryPath("our home for ${sdk.sdkName}")
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
      "fix: UnknownMissingSdkFixDownload",
      "sdk-name: prototype (2)",
      "download: null",
      "configure: prototype (2)",
      "sdk: prototype (2)",
    )
    removeSdk("prototype (2)")
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
    val sdkModificator = sdk.sdkModificator
    sdkModificator.versionString = version
    runWriteAction { sdkModificator.commitChanges() }
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
