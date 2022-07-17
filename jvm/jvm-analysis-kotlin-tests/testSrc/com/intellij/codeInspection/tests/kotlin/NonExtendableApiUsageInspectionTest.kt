package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.NonExtendableApiUsageInspection
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

@TestDataPath("/testData/codeInspection/nonExtendableApiUsage")
class NonExtendableApiUsageInspectionTest : LightJavaCodeInsightFixtureTestCase() {
  private val projectDescriptor = object : ProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      PsiTestUtil.addProjectLibrary(model, "annotations", listOf(PathUtil.getJarPathForClass(ApiStatus.NonExtendable::class.java)))
      PsiTestUtil.addProjectLibrary(model, "library", listOf(testDataPath))
    }
  }

  private var inspection = NonExtendableApiUsageInspection()

  override fun getBasePath() = "/jvm/jvm-analysis-kotlin-tests/testData/codeInspection/nonExtendableApiUsage"

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
  }

  override fun getProjectDescriptor() = projectDescriptor

  override fun tearDown() {
    try {
      myFixture.disableInspections(inspection)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test java extensions`() {
    myFixture.testHighlighting("plugin/javaExtensions.java")
  }

  fun `test kotlin extensions`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/kotlinExtensions.kt")
  }

}