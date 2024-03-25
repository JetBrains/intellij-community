// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.use
import com.intellij.testFramework.LightPlatformTestCase
import org.jdom.Element
import java.io.File
import java.util.*
import java.util.function.Consumer
import javax.swing.JComponent

abstract class SdkTestCase : LightPlatformTestCase() {

  override fun setUp() {
    super.setUp()

    TestSdkGenerator.reset()
    SdkType.EP_NAME.point.registerExtension(TestSdkType, testRootDisposable)
    SdkType.EP_NAME.point.registerExtension(DependentTestSdkType, testRootDisposable)
    SdkDownload.EP_NAME.point.registerExtension(TestSdkDownloader, testRootDisposable)
  }

  fun createAndRegisterSdk(isProjectSdk: Boolean = false): Sdk {
    val sdk = TestSdkGenerator.createNextSdk()
    registerSdk(sdk, isProjectSdk)
    return sdk
  }

  fun createAndRegisterDependentSdk(isProjectSdk: Boolean = false): Sdk {
    val parentSdk = TestSdkGenerator.createNextSdk()
    registerSdk(parentSdk)

    val sdk = TestSdkGenerator.createNextDependentSdk(parentSdk)
    registerSdk(sdk, isProjectSdk)
    return sdk
  }

  private fun registerSdk(sdk: Sdk, isProjectSdk: Boolean = false) {
    registerSdk(sdk, testRootDisposable)
    if (isProjectSdk) {
      setProjectSdk(project, sdk, testRootDisposable)
    }
  }

  fun <R> withProjectSdk(sdk: Sdk, action: () -> R): R {
    return withProjectSdk(project, sdk, action)
  }

  interface TestSdkType : JavaSdkType, SdkTypeId {
    companion object : SdkType("test-type"), TestSdkType {
      override fun getPresentableName(): String = name
      override fun isValidSdkHome(path: String): Boolean = true
      override fun suggestSdkName(currentSdkName: String?, sdkHome: String): String = TestSdkGenerator.findTestSdk(sdkHome)!!.name
      override fun suggestHomePath(): String? = null
      override fun suggestHomePaths(): Collection<String> = TestSdkGenerator.getAllTestSdks().map { it.homePath!! }
      override fun createAdditionalDataConfigurable(sdkModel: SdkModel, sdkModificator: SdkModificator): AdditionalDataConfigurable? = null
      override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {}
      override fun getBinPath(sdk: Sdk): String = File(sdk.homePath, "bin").path
      override fun getToolsPath(sdk: Sdk): String = File(sdk.homePath, "lib/tools.jar").path
      override fun getVMExecutablePath(sdk: Sdk): String = File(sdk.homePath, "bin/java").path
      override fun getVersionString(sdkHome: String): String? = TestSdkGenerator.findTestSdk(sdkHome)?.versionString
    }
  }

  internal val Sdk.parent: Sdk
    get() {
      if (sdkType != DependentTestSdkType) error("Unexpected state")
      val parentSdkName = (sdkAdditionalData as DependentTestSdkAdditionalData).patentSdkName
      return ProjectJdkTable.getInstance().findJdk(parentSdkName)!!
    }

  class DependentTestSdkAdditionalData(val patentSdkName: String) : SdkAdditionalData

  object DependentTestSdkType : DependentSdkType("dependent-test-type"), TestSdkType {
    private fun getParentPath(sdk: Sdk, relativePath: String): String? {
      if (sdk.sdkType != DependentTestSdkType) return null
      val additionalData = sdk.sdkAdditionalData
      val parentSdkName = (additionalData as DependentTestSdkAdditionalData).patentSdkName
      return ProjectJdkTable.getInstance().findJdk(parentSdkName)?.homePath?.let { File(it, relativePath).path }
    }

    override fun getPresentableName(): String = name
    override fun isValidSdkHome(path: String): Boolean = true
    override fun suggestSdkName(currentSdkName: String?, sdkHome: String): String = "dependent-sdk-name"
    override fun suggestHomePath(): String? = null
    override fun createAdditionalDataConfigurable(sdkModel: SdkModel, sdkModificator: SdkModificator): AdditionalDataConfigurable? = null
    override fun getBinPath(sdk: Sdk) = getParentPath(sdk, "bin")
    override fun getToolsPath(sdk: Sdk) = getParentPath(sdk, "lib/tools.jar")
    override fun getVMExecutablePath(sdk: Sdk) = getParentPath(sdk, "bin/java")

    override fun getUnsatisfiedDependencyMessage() = "Unsatisfied dependency message"
    override fun isValidDependency(sdk: Sdk) = sdk is TestSdkType
    override fun getDependencyType() = TestSdkType

    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {
      additional.setAttribute("patentSdkName", (additionalData as DependentTestSdkAdditionalData).patentSdkName)
    }

    override fun loadAdditionalData(additional: Element): SdkAdditionalData {
      return DependentTestSdkAdditionalData(additional.getAttributeValue("patentSdkName") ?: "")
    }
  }

  object TestSdkDownloader : SdkDownload {
    override fun supportsDownload(sdkTypeId: SdkTypeId) = sdkTypeId == TestSdkType

    override fun showDownloadUI(
      sdkTypeId: SdkTypeId,
      sdkModel: SdkModel,
      parentComponent: JComponent,
      selectedSdk: Sdk?,
      sdkCreatedCallback: Consumer<in SdkDownloadTask>
    ) {
      val sdk = TestSdkGenerator.createNextSdk()
      sdkCreatedCallback.accept(object : SdkDownloadTask {
        override fun doDownload(indicator: ProgressIndicator) {}
        override fun getPlannedVersion() = sdk.versionString!!
        override fun getSuggestedSdkName() = sdk.name
        override fun getPlannedHomeDir() = sdk.homePath!!
      })
    }

    override fun pickSdk(sdkTypeId: SdkTypeId,
                         sdkModel: SdkModel,
                         parentComponent: JComponent,
                         selectedSdk: Sdk?): SdkDownloadTask? = null
  }

  object TestSdkGenerator {
    private var createdSdkCounter = 0
    private lateinit var createdSdks: MutableMap<String, Sdk>

    fun getAllTestSdks() = createdSdks.values

    fun findTestSdk(sdk: Sdk): Sdk? = findTestSdk(sdk.homePath!!)

    fun findTestSdk(homePath: String): Sdk? = createdSdks[FileUtil.toSystemDependentName(homePath)]

    fun getCurrentSdk() = createdSdks.values.last()

    fun reserveNextSdk(versionString: String = "11"): SdkInfo {
      val name = "test $versionString (${createdSdkCounter++})"
      val homePath = FileUtil.toCanonicalPath(FileUtil.join(FileUtil.getTempDirectory(), "jdk-$name"))
      return SdkInfo(name, versionString, homePath)
    }

    fun createTestSdk(sdkInfo: SdkInfo): Sdk {
      val sdk = ProjectJdkTable.getInstance().createSdk(sdkInfo.name, TestSdkType)
      val sdkModificator = sdk.sdkModificator
      sdkModificator.homePath = sdkInfo.homePath
      sdkModificator.versionString = sdkInfo.versionString

      val application = ApplicationManager.getApplication()
      val runnable = { sdkModificator.commitChanges() }
      if (application.isDispatchThread) {
        application.runWriteAction(runnable)
      } else {
        application.invokeAndWait { application.runWriteAction(runnable) }
      }
      createdSdks[FileUtil.toSystemDependentName(sdkInfo.homePath)] = sdk
      return sdk
    }

    fun createNextSdk(versionString: String = "11"): Sdk {
      val sdkInfo = reserveNextSdk(versionString)
      generateJdkStructure(sdkInfo)
      return createTestSdk(sdkInfo)
    }

    fun createNextDependentSdk(parentSdk: Sdk): Sdk {
      val name = "dependent-test-name (${createdSdkCounter++})"
      val versionString = "11"
      val homePath = FileUtil.getTempDirectory() + "/jdk-$name"

      val sdk = ProjectJdkTable.getInstance().createSdk(name, DependentTestSdkType)
      val sdkModificator = sdk.sdkModificator
      sdkModificator.homePath = homePath
      sdkModificator.versionString = versionString
      sdkModificator.sdkAdditionalData = DependentTestSdkAdditionalData(parentSdk.name)
      ApplicationManager.getApplication().runWriteAction { sdkModificator.commitChanges() }
      createdSdks[homePath] = sdk
      return sdk
    }

    fun generateJdkStructure(sdkInfo: SdkInfo) {
      val homePath = sdkInfo.homePath
      createFile("$homePath/release")
      createFile("$homePath/jre/lib/rt.jar")
      createFile("$homePath/bin/javac")
      createFile("$homePath/bin/java")
      val properties = Properties()
      properties.setProperty("JAVA_FULL_VERSION", sdkInfo.versionString)
      File("$homePath/release").outputStream().use {
        properties.store(it, null)
      }
    }

    private fun createFile(path: String) {
      val file = File(path)
      file.parentFile.mkdirs()
      file.createNewFile()
    }

    fun reset() {
      createdSdkCounter = 0
      createdSdks = LinkedHashMap()
    }

    data class SdkInfo(val name: String, val versionString: String, val homePath: String)
  }

  companion object {

    inline fun <R> assertUnexpectedSdksRegistration(action: () -> R): R {
      return assertNewlyRegisteredSdks({ null }, action = action)
    }

    inline fun <R> assertNewlyRegisteredSdks(getExpectedNewSdk: () -> Sdk?, isAssertSdkName: Boolean = true, action: () -> R): R {
      val projectSdkTable = ProjectJdkTable.getInstance()
      val beforeSdks = projectSdkTable.allJdks.toSet()

      val result = runCatching(action)

      val afterSdks = projectSdkTable.allJdks.toSet()
      val newSdks = afterSdks - beforeSdks
      removeSdks(*newSdks.toTypedArray())

      result.onSuccess {
        val expectedNewSdk = getExpectedNewSdk()
        if (expectedNewSdk != null) {
          assertTrue("Expected registration of $expectedNewSdk but found $newSdks", newSdks.size == 1)
          val newSdk = newSdks.single()
          assertSdk(expectedNewSdk, newSdk, isAssertSdkName)
        }
        else {
          assertTrue("Unexpected sdk registration $newSdks", newSdks.isEmpty())
        }
      }

      return result.getOrThrow()
    }

    fun assertSdk(expected: Sdk?, actual: Sdk?, isAssertSdkName: Boolean = true) {
      if (expected != null && actual != null) {
        if (isAssertSdkName) {
          assertEquals(expected.name, actual.name)
        }
        assertEquals(expected.sdkType, actual.sdkType)
        assertEquals(expected, TestSdkGenerator.findTestSdk(actual))
      }
      else {
        assertEquals(expected, actual)
      }
    }

    fun registerSdk(sdk: Sdk, parentDisposable: Disposable) {
      WriteAction.runAndWait<Throwable> {
        val jdkTable = ProjectJdkTable.getInstance()
        jdkTable.addJdk(sdk, parentDisposable)
      }
    }

    fun registerSdks(vararg sdks: Sdk, parentDisposable: Disposable) {
      sdks.forEach { registerSdk(it, parentDisposable) }
    }

    fun removeSdk(sdk: Sdk) {
      WriteAction.runAndWait<Throwable> {
        val jdkTable = ProjectJdkTable.getInstance()
        jdkTable.removeJdk(sdk)
      }
    }

    fun removeSdks(vararg sdks: Sdk) {
      sdks.forEach(::removeSdk)
    }

    fun setProjectSdk(project: Project, sdk: Sdk?, parentDisposable: Disposable) {
      val rootManager = ProjectRootManager.getInstance(project)
      val projectSdk = rootManager.projectSdk
      WriteAction.runAndWait<Throwable> {
        rootManager.projectSdk = sdk
      }
      Disposer.register(parentDisposable, Disposable {
        WriteAction.runAndWait<Throwable> {
          rootManager.projectSdk = projectSdk
        }
      })
    }

    inline fun <R> withProjectSdk(project: Project, sdk: Sdk, action: () -> R): R {
      return Disposer.newDisposable().use { disposable ->
        setProjectSdk(project, sdk, parentDisposable = disposable)
        action()
      }
    }

    inline fun <R> withRegisteredSdks(vararg sdks: Sdk, action: () -> R): R {
      return Disposer.newDisposable().use { disposable ->
        registerSdks(*sdks, parentDisposable = disposable)
        action()
      }
    }
  }
}