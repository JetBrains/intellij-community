package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.OverrideOnlyInspection
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

@TestDataPath("/testData/codeInspection/overrideOnly")
abstract class KotlinOverrideOnlyInspectionTest : JvmInspectionTestBase(), KotlinPluginModeProvider {
  override val inspection: InspectionProfileEntry = OverrideOnlyInspection()

  override fun getProjectDescriptor() = object : ProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      PsiTestUtil.addProjectLibrary(model, "annotations", listOf(PathUtil.getJarPathForClass(ApiStatus.OverrideOnly::class.java)))
      PsiTestUtil.addProjectLibrary(model, "library", listOf(testDataPath))
    }
  }

  override fun getBasePath() = "/jvm/jvm-analysis-kotlin-tests-shared/testData/codeInspection/overrideOnly"

  fun `test java invocations`() {
    myFixture.testHighlighting("plugin/JavaCode.java")
  }

  fun `test kotlin invocations`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/KotlinCode.kt")
  }

  fun `test java delegation`() {
    myFixture.testHighlighting("plugin/DelegateJavaCode.java")
  }

  fun `test kotlin delegation`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/DelegateKotlinCode.kt")
  }
}