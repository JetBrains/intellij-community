// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.UnstableApiUsageInspection
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/unstableApiUsage")
class UnstableApiUsageInspectionTest : JavaCodeInsightFixtureTestCase() {

  private val inspection = UnstableApiUsageInspection()

  override fun getBasePath() = "${JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH}/codeInspection/unstableApiUsage"

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(ApiStatus::class.java))
  }

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
    // otherwise assertion in PsiFileImpl ("Access to tree elements not allowed") will not pass
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

  fun testJavaInspection() {
    inspection.myIgnoreInsideImports = false
    myFixture.testHighlighting(true, false, false, "UnstableElementsTest.java")
  }

  fun testJavaIgnoreImports() {
    inspection.myIgnoreInsideImports = true
    myFixture.testHighlighting(true, false, false, "UnstableElementsIgnoreImportsTest.java")
  }

  fun testKotlinInspection() {
    inspection.myIgnoreInsideImports = false
    myFixture.testHighlighting("UnstableElementsTest.kt")
  }

  fun testKotlinIgnoreImports() {
    inspection.myIgnoreInsideImports = true
    myFixture.testHighlighting("UnstableElementsIgnoreImportsTest.kt")
  }
}