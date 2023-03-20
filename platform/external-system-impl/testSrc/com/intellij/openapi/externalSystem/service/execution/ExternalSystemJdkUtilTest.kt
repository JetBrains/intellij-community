// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.JavaDependentSdkType
import com.intellij.openapi.projectRoots.impl.MockSdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.EnvironmentUtil
import com.intellij.util.SystemProperties
import com.intellij.util.ThrowableRunnable
import com.intellij.util.containers.MultiMap
import com.intellij.util.lang.JavaVersion
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File

class ExternalSystemJdkUtilTest : UsefulTestCase() {

  lateinit var testFixture: IdeaProjectTestFixture
  lateinit var project: Project

  override fun setUp() {
    super.setUp()
    testFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name, true).fixture
    testFixture.setUp()
    project = testFixture.project

    allowJavaHomeAccess()
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { testFixture.tearDown() },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  private fun allowJavaHomeAccess() {
    val javaHome = EnvironmentUtil.getValue("JAVA_HOME") ?: return
    VfsRootAccess.allowRootAccess(project, javaHome)
  }

  @Test
  fun testGetJdk() {
    assertThat(getJdk(project, null)).isNull()

    assertThat(resolveJdkName(null, USE_INTERNAL_JAVA)?.homePath)
      .isEqualTo(StringUtil.trimEnd(FileUtil.toSystemIndependentName(SystemProperties.getJavaHome()), "/jre"))

    val javaHomeEnv = EnvironmentUtil.getValue("JAVA_HOME")?.let { FileUtil.toSystemIndependentName(it) }
    if (javaHomeEnv.isNullOrBlank()) {
      assertThrows(UndefinedJavaHomeException::class.java) { getJdk(project, USE_JAVA_HOME) }
    }
    else {
      assertThat(getJdk(project, USE_JAVA_HOME)?.homePath)
        .isEqualTo(javaHomeEnv)
    }

    val sdk = IdeaTestUtil.getMockJdk9()
    WriteAction.run<Throwable> {
      ProjectJdkTable.getInstance().addJdk(sdk, testFixture.testRootDisposable)
      ProjectRootManager.getInstance(project).projectSdk = sdk
    }

    assertThat(getJdk(project, USE_PROJECT_JDK))
      .isEqualTo(sdk)
  }

  @Test
  fun testResolveJdkName() {
    // Kotlin generates private `testResolveJdkName$lambda-6` and `testResolveJdkName$lambda-7` methods,
    // and JUnit yields a warning about non-public test method.
    // Separate `doTestResolveJdkName` method makes Kotlin generate the same methods with another names,
    // which are not considered as tests by JUnit.
    doTestResolveJdkName()
  }

  private fun doTestResolveJdkName() {
    assertThat(resolveJdkName(null, null)).isNull()

    assertThat(resolveJdkName(null, USE_INTERNAL_JAVA)?.homePath)
      .isEqualTo(StringUtil.trimEnd(FileUtil.toSystemIndependentName(SystemProperties.getJavaHome()), "/jre"))

    val javaHomeEnv = EnvironmentUtil.getValue("JAVA_HOME")?.let { FileUtil.toSystemIndependentName(it) }
    if (javaHomeEnv.isNullOrBlank()) {
      assertThrows(UndefinedJavaHomeException::class.java) { resolveJdkName(null, USE_JAVA_HOME) }
    }
    else {
      assertThat(resolveJdkName(null, USE_JAVA_HOME)?.homePath)
        .isEqualTo(javaHomeEnv)
    }

    assertThrows(ProjectJdkNotFoundException::class.java) {
      resolveJdkName(null, USE_PROJECT_JDK)
    }
    val sdk: Sdk = mock(Sdk::class.java)
    assertThat(resolveJdkName(sdk, USE_PROJECT_JDK))
      .isEqualTo(sdk)
  }

  @Test
  fun testGetAvailableJdkChoosesLatestSdk() {

    val sdk8 = createMockJdk(JavaVersion.compose(8))
    val sdk9 = createMockJdk(JavaVersion.compose(9))

    WriteAction.run<Throwable> {
      ProjectJdkTable.getInstance().addJdk(sdk8, testFixture.testRootDisposable)
      ProjectJdkTable.getInstance().addJdk(sdk9, testFixture.testRootDisposable)
    }

    assertThat(getAvailableJdk(project).second).isEqualTo(sdk9)
  }

  @Test
  fun testGetAvailableJdkPrefersProjectSDKDependency() {
    val sdk8 = createMockJdk(JavaVersion.compose(8))
    val sdk9 = createMockJdk(JavaVersion.compose(9))

    val dependentSDK = TestJavaDependentSdk(sdk8)

    WriteAction.run<Throwable> {
      with(ProjectJdkTable.getInstance()) {
        addJdk(sdk8, testFixture.testRootDisposable)
        addJdk(sdk9, testFixture.testRootDisposable)
        addJdk(dependentSDK, testFixture.testRootDisposable)
      }
      ProjectRootManager.getInstance(project).projectSdk = dependentSDK
    }

    assertThat(getAvailableJdk(project).second).isEqualTo(sdk8)
  }


  private fun createMockJdk(jdkVersion: JavaVersion): Sdk {
    val jdkVersionStr = jdkVersion.toString()
    val jdkDir = FileUtil.createTempDirectory(jdkVersionStr, null)
    listOf("bin/javac",
           "bin/java",
           "jre/lib/rt.jar")
      .forEach {
        File(jdkDir, it).apply {
          parentFile.mkdirs()
          createNewFile()
          writeText("Fake")
        }
      }

    val path = jdkDir.absolutePath
    assertThat(isValidJdk(path)).`as`("Mock JDK at $path is expected to pass validation by com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.isValidJdk() " +
                                      "Please, check validation code and update mock accordingly").isTrue()
    return IdeaTestUtil.createMockJdk(jdkVersionStr, path)
  }
}

class TestJavaDependentSdk(val sdk: Sdk) : MockSdk("TestJavaDependentSdk",
                                                   "fake/path",
                                                   "1.0",
                                                   MultiMap.empty<OrderRootType, VirtualFile>(),
                                                   TestJavaDependentSdkType.getInstance())

class TestJavaDependentSdkType(val myName: String): JavaDependentSdkType(myName) {
  companion object {
    private val instance = TestJavaDependentSdkType("TestSdkType")
    fun getInstance(): TestJavaDependentSdkType = instance
  }

  override fun suggestHomePath(): String? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isValidSdkHome(path: String): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun suggestSdkName(currentSdkName: String?, sdkHome: String): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createAdditionalDataConfigurable(sdkModel: SdkModel, sdkModificator: SdkModificator): AdditionalDataConfigurable? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getPresentableName(): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getBinPath(sdk: Sdk): String {
    return (sdk as? TestJavaDependentSdk)?.let { JavaSdk.getInstance().getBinPath(it.sdk) }!!
  }

  override fun getToolsPath(sdk: Sdk): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getVMExecutablePath(sdk: Sdk): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

}
