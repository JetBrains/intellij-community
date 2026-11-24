// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ui.configuration.UnknownSdk
import com.intellij.openapi.roots.ui.configuration.UnknownSdkMultipleDownloadsFix
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable
import com.intellij.util.lang.JavaVersion
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.model.java.LanguageLevel
import org.junit.Assert
import org.junit.Test
import java.nio.file.Path
import java.util.function.Predicate

class JdkAutoTest : JavaCodeInsightFixtureTestCase() {
  private lateinit var indicator: ProgressIndicator
  private lateinit var jdks : Map<Int, Sdk>

  override fun setUp() {
    super.setUp()
    indicator = EmptyProgressIndicator()

    jdks = LanguageLevel.entries
      .filterNot { it.isPreview }
      .associate {
        val javaVersion = it.toJavaVersion()
        val sdk = registerMockJdk(javaVersion)
        javaVersion.feature to sdk
      }

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

  private val openJdk11 = jdkItem("Oracle", "OpenJDK", 11, "11.0.2", "openjdk-11", isEol = true)
  private val corretto11 = jdkItem("Amazon", "Corretto", 11, "11.0.25", "corretto-11")
  private val msOpenJdk11 = jdkItem("Microsoft", "OpenJDK", 11, "11.0.33.1", "ms-11")
  private val openJdk12 = jdkItem("Oracle", "OpenJDK", 12, "12.0.2", "openjdk-12", isEol = true)

  @Test
  fun `test download filters eol jdks when non-eol alternatives exist`() {
    assertDownloadProposal(
      sdkName = "11",
      feed = listOf(openJdk11, corretto11, msOpenJdk11),
      expected = listOf(corretto11, msOpenJdk11),
    )
  }

  @Test
  fun `test download selects single non-eol jdk when eol is filtered out`() {
    assertDownloadProposal(
      sdkName = "11",
      feed = listOf(openJdk11, corretto11),
      expected = listOf(corretto11),
    )
  }

  @Test
  fun `test download keeps eol jdk when requested with openjdk prefix`() {
    assertDownloadProposal(
      sdkName = "openjdk-11",
      feed = listOf(openJdk11, corretto11, msOpenJdk11),
      expected = listOf(openJdk11),
    )
  }

  @Test
  fun `test download keeps eol jdk when requested with versioned openjdk prefix`() {
    assertDownloadProposal(
      sdkName = "openjdk-11.0.2",
      feed = listOf(openJdk11, corretto11, msOpenJdk11),
      expected = listOf(openJdk11, msOpenJdk11),
    )
  }

  @Test
  fun `test download keeps eol jdk when no non-eol alternative exists`() {
    assertDownloadProposal(
      sdkName = "12",
      feed = listOf(openJdk12, corretto11),
      expected = listOf(openJdk12),
    )
  }

  @Test
  fun `test download keeps eol jdk when sdk name matches suggested name with expected version`() {
    assertDownloadProposal(
      sdkName = "openjdk-11",
      expectedVersion = "11.0.2",
      feed = listOf(openJdk11, corretto11),
      expected = listOf(openJdk11),
    )
  }

  private fun assertDownloadProposal(
    sdkName: String,
    feed: List<JdkItem>,
    expected: List<JdkItem>,
    expectedVersion: String? = null,
  ) {
    val resolver = requireNotNull(JdkAuto().createResolverImpl(project, indicator) { feed })
    val proposal = resolver.proposeDownload(SdkRequest(mySdkName = sdkName, myExpectedVersionString = expectedVersion), indicator)

    if (expected.isEmpty()) {
      Assert.assertNull("Expected no download fix, got $proposal", proposal)
      return
    }

    requireNotNull(proposal)
    if (expected.size == 1) {
      Assert.assertFalse("Expected single download fix, got $proposal", proposal is UnknownSdkMultipleDownloadsFix<*>)
      val expectedText = expected.single().fullPresentationText
      Assert.assertTrue(
        "Expected $expectedText, got ${proposal.downloadDescription}",
        proposal.downloadDescription.startsWith("$expectedText ("),
      )
    }
    else {
      Assert.assertTrue("Expected multiple download fix, got $proposal", proposal is UnknownSdkMultipleDownloadsFix<*>)
      val expectedItems = expected.joinToString(" / ") { it.fullPresentationText }
      Assert.assertTrue(
        "Expected [$expectedItems], got $proposal",
        proposal.toString().startsWith("UnknownSdkMultipleDownloadsFix{$expectedItems,"),
      )
    }
  }

  private fun jdkItem(
    vendor: String,
    product: String,
    major: Int,
    version: String,
    suggestedSdkName: String,
    isEol: Boolean = false,
  ): JdkItem {
    return JdkItem(
      product = JdkProduct(vendor = vendor, product = product, flavour = null),
      isDefaultItem = false,
      isVisibleOnUI = true,
      isPreview = false,
      isEol = isEol,
      jdkMajorVersion = major,
      jdkVersion = version,
      jdkVendorVersion = null,
      suggestedSdkName = suggestedSdkName,
      os = JdkPredicate.currentOS,
      arch = JdkPredicate.currentArch,
      packageType = JdkPackageType.TAR_GZ,
      url = "https://example.com/$suggestedSdkName.tar.gz",
      sha256 = "sha256-$suggestedSdkName",
      archiveSize = 100L * 1024 * 1024,
      unpackedSize = 200L * 1024 * 1024,
      packageRootPrefix = "",
      packageToBinJavaPrefix = "",
      archiveFileName = "$suggestedSdkName.tar.gz",
      installFolderName = suggestedSdkName,
      sharedIndexAliases = listOf(),
      saveToFile = {},
    )
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
    val mySdkHomePredicate: Predicate<String>? = null,
    val myExpectedVersionString: String? = null,
  ): UnknownSdk {
    override fun getSdkType() = mySdkType
    override fun getSdkName() = mySdkName ?: super.getSdkName()
    override fun getSdkVersionStringPredicate() = mySdkVersionPredicate ?: super.getSdkVersionStringPredicate()
    override fun getSdkHomePredicate(): Predicate<String>?  = mySdkHomePredicate ?: super.getSdkHomePredicate()
    override fun getExpectedVersionString(): String? = myExpectedVersionString ?: super.getExpectedVersionString()
  }

  private fun registerSdkType(anotherJavaSdkType: AnotherJavaSdkType, disposable: Disposable) {
    SdkType.EP_NAME.point.registerExtension(anotherJavaSdkType, disposable)
  }

  private class AnotherJavaSdkType : SdkType("AnotherJavaSdk"), JavaSdkType {
    override fun suggestHomePath(path: Path): String? = null

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
