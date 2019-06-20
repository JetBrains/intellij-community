package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.ScheduledForRemovalInspection
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/scheduledForRemoval")
class ScheduledForRemovalInspectionTest: JavaCodeInsightFixtureTestCase() {

  private val inspection = ScheduledForRemovalInspection()

  override fun getBasePath() = "${TEST_DATA_PROJECT_RELATIVE_BASE_PATH}/codeInspection/scheduledForRemoval"

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(ApiStatus::class.java))
  }

  override fun setUp() {
    super.setUp()
    // otherwise assertion in PsiFileImpl ("Access to tree elements not allowed") will not pass
    myFixture.enableInspections(inspection)
    (myFixture as CodeInsightTestFixtureImpl).setVirtualFileFilter(VirtualFileFilter.NONE)
    configureAnnotatedFiles()
  }

  private fun configureAnnotatedFiles() {
    listOf(
      "annotatedPkg/ClassInAnnotatedPkg.java",
      "annotatedPkg/package-info.java",
      "pkg/AnnotatedAnnotation.java",
      "pkg/AnnotatedClass.java",
      "pkg/AnnotatedEnum.java",
      "pkg/NonAnnotatedAnnotation.java",
      "pkg/NonAnnotatedClass.java",
      "pkg/NonAnnotatedEnum.java"
    ).forEach { myFixture.copyFileToProject(it) }
  }

  fun testKotlinInspection() {
    inspection.myIgnoreInsideImports = false
    myFixture.testHighlighting("ScheduledForRemovalElementsTest.kt")
  }

  fun testKotlinIgnoreImports() {
    inspection.myIgnoreInsideImports = true
    myFixture.testHighlighting("ScheduledForRemovalElementsIgnoreImportsTest.kt")
  }

  fun testJavaInspection() {
    inspection.myIgnoreInsideImports = false
    myFixture.testHighlighting(true, false, false, "ScheduledForRemovalElementsTest.java")
  }

  fun testJavaIgnoreImports() {
    inspection.myIgnoreInsideImports = true
    myFixture.testHighlighting(true, false, false, "ScheduledForRemovalElementsIgnoreImportsTest.java")
  }
}