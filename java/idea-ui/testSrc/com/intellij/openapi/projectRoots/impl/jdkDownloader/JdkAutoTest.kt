// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.roots.ui.configuration.UnknownSdk
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable
import com.intellij.util.lang.JavaVersion
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.model.java.LanguageLevel
import org.junit.Assert
import org.junit.Test
import java.util.function.Predicate

class JdkAutoTest : JavaCodeInsightFixtureTestCase() {
  private lateinit var indicator: ProgressIndicator
  private lateinit var jdks : Map<Int, Sdk>

  override fun setUp() {
    super.setUp()
    indicator = EmptyProgressIndicator()

    val values : Array<LanguageLevel> = LanguageLevel.values()
    jdks = values.filterNot { it.isPreview }.map {
      val javaVersion = it.toJavaVersion()
      val sdk = registerMockJdk(javaVersion)
      javaVersion.feature to sdk
    }.toMap()

    registerSdkType(AnotherJavaSdkType(), testRootDisposable)
  }

  @Test
  fun `test '1_8' jdk`() {
    val resolver = JdkAuto().createResolverImpl(project, indicator)
    requireNotNull(resolver)

    val proposal = resolver.proposeLocalFix(
      SdkRequest(mySdkName = "1.8"),
      indicator
    )

    requireNotNull(proposal)
    Assert.assertEquals(jdks.getValue(8).homePath, proposal.existingSdkHome)
  }

  @Test
  fun `test '8' jdk`() {
    val resolver = JdkAuto().createResolverImpl(project, indicator)
    requireNotNull(resolver)

    val proposal = resolver.proposeLocalFix(
      SdkRequest(mySdkName = "8"),
      indicator
    )

    requireNotNull(proposal)
    Assert.assertEquals(jdks.getValue(8).homePath, proposal.existingSdkHome)
  }

  @Test
  fun `test '11' jdk`() {
    val resolver = JdkAuto().createResolverImpl(project, indicator)
    requireNotNull(resolver)

    val proposal = resolver.proposeLocalFix(
      SdkRequest(mySdkName = "11"),
      indicator
    )

    requireNotNull(proposal)
    Assert.assertEquals(jdks.getValue(11).homePath, proposal.existingSdkHome)
  }

  @Test
  fun `test 'IDEA jdk' Must Not Suggest Any SDK`() {
    val resolver = JdkAuto().createResolverImpl(project, indicator)
    requireNotNull(resolver)

    val proposal = resolver.proposeLocalFix(
      SdkRequest(mySdkName = "IDEA jdk"),
      indicator
    )

    Assert.assertNull(proposal)
  }

  @Test
  fun `test 'IDEA jdk' Must Not Suggest Any SDK 2`() {
    val resolver = JdkAuto().createResolverImpl(project, indicator)
    requireNotNull(resolver)

    val proposal = resolver.proposeLocalFix(
      SdkRequest(mySdkName = "IDEA jdk", mySdkHomePredicate = Predicate { true }),
      indicator
    )

    Assert.assertNull(proposal)
  }

  @Test
  fun `test with filter jdk`() {
    val resolver = JdkAuto().createResolverImpl(project, indicator)
    requireNotNull(resolver)

    val proposal = resolver.proposeLocalFix(
      SdkRequest(mySdkVersionPredicate = Predicate { true }),
      indicator
    )

    requireNotNull(proposal)
    //the most recent JDK should be returned
    Assert.assertEquals(jdks.getValue(jdks.keys.maxOrNull()!!).homePath, proposal.existingSdkHome)
  }

  @Test
  fun `test most recent Jdk with same feature version`() {
    val resolver = JdkAuto().createResolverImpl(project, indicator)
    requireNotNull(resolver)

    registerMockJdk(JavaVersion.tryParse("11.0.5")!!)
    val v7 = registerMockJdk(JavaVersion.tryParse("11.0.7")!!)

    val proposal = resolver.proposeLocalFix(
      SdkRequest(mySdkName = "11"),
      indicator
    )

    requireNotNull(proposal)

    //the most recent JDK should be returned
    Assert.assertEquals(JavaVersion.tryParse(v7.versionString)!!, JavaVersion.tryParse(proposal.versionString)!!)
  }

  private fun registerMockJdk(javaVersion: @NotNull JavaVersion): Sdk {
    val sdk = IdeaTestUtil.getMockJdk(javaVersion)

    val jdkTable = ProjectJdkTable.getInstance()
    val foundJdk = jdkTable.findJdk(sdk.name)
    if (foundJdk != null) return sdk

    val addSdk = ThrowableRunnable<RuntimeException> {
      jdkTable.addJdk(sdk, myFixture.projectDisposable)
    }

    if (ApplicationManager.getApplication().isWriteAccessAllowed) {
      addSdk.run()
    } else {
      WriteAction.run(addSdk)
    }

    return sdk
  }

  private data class SdkRequest(
    val mySdkType: SdkType = JavaSdk.getInstance(),
    val mySdkName: String? = null,
    val mySdkVersionPredicate :Predicate<String>? = null,
    val mySdkHomePredicate: Predicate<String>? = null
  ): UnknownSdk {
    override fun getSdkType() = mySdkType
    override fun getSdkName() = mySdkName ?: super.getSdkName()
    override fun getSdkVersionStringPredicate() = mySdkVersionPredicate ?: super.getSdkVersionStringPredicate()
    override fun getSdkHomePredicate(): Predicate<String>?  = mySdkHomePredicate ?: super.getSdkHomePredicate()
  }

  private fun registerSdkType(anotherJavaSdkType: AnotherJavaSdkType, disposable: Disposable) {
    SdkType.EP_NAME.point.registerExtension(anotherJavaSdkType, disposable)
  }

  private class AnotherJavaSdkType : SdkType("AnotherJavaSdk"), JavaSdkType {
    override fun suggestHomePath(): String? = null

    override fun isValidSdkHome(path: String): Boolean = false

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String): String = ""

    override fun createAdditionalDataConfigurable(sdkModel: SdkModel, sdkModificator: SdkModificator): AdditionalDataConfigurable? = null

    override fun getPresentableName(): String = "AnotherJavaSdk"

    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {
      additional.setAttribute("data", (additionalData as MockSdkAdditionalData).data)
    }

    override fun loadAdditionalData(additional: Element): SdkAdditionalData {
      return MockSdkAdditionalData(additional.getAttributeValue("data") ?: "")
    }

    override fun getBinPath(sdk: Sdk): String {
      TODO("Not yet implemented")
    }

    override fun getToolsPath(sdk: Sdk): String {
      TODO("Not yet implemented")
    }

    override fun getVMExecutablePath(sdk: Sdk): String {
      TODO("Not yet implemented")
    }
  }

  private class MockSdkAdditionalData(var data: String) : SdkAdditionalData
}
