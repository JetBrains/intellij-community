package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.NonExtendableApiUsageInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

@TestDataPath("/testData/codeInspection/nonExtendableApiUsage")
abstract class KotlinNonExtendableApiUsageInspectionTest : JvmInspectionTestBase(), KotlinPluginModeProvider {
  override val inspection = NonExtendableApiUsageInspection()

  override fun getProjectDescriptor() = object : ProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      PsiTestUtil.addProjectLibrary(model, "annotations", listOf(PathUtil.getJarPathForClass(ApiStatus.NonExtendable::class.java)))
      PsiTestUtil.addProjectLibrary(model, "library", listOf(testDataPath))
    }
  }

  override fun getBasePath() = "/jvm/jvm-analysis-kotlin-tests-shared/testData/codeInspection/nonExtendableApiUsage"

  fun `test java extensions`() {
    myFixture.testHighlighting("plugin/javaExtensions.java")
  }

  fun `test kotlin extensions`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/kotlinExtensions.kt")
  }
}