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

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/unstableApiUsage/experimental")
class UnstableApiUsageInspectionTest : JavaCodeInsightFixtureTestCase() {

  private val inspection = UnstableApiUsageInspection()

  override fun getBasePath() = "${JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH}/codeInspection/unstableApiUsage/experimental"

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
      "pkg/NonAnnotatedEnum.java",
      "pkg/ClassWithExperimentalTypeInSignature.java",
      "pkg/OwnerOfMembersWithExperimentalTypesInSignature.java"
    ).forEach { myFixture.copyFileToProject(it) }
  }

  fun `test java unstable api usages`() {
    inspection.myIgnoreInsideImports = false
    myFixture.testHighlighting("UnstableElementsTest.java")
  }

  fun `test java do not report unstable api usages inside import statements`() {
    inspection.myIgnoreInsideImports = true
    myFixture.testHighlighting("UnstableElementsIgnoreImportsTest.java")
  }

  fun `test java no warnings on access to members of the same file`() {
    myFixture.testHighlighting("NoWarningsMembersOfTheSameFile.java")
  }

  fun `test kotlin no warnings on access to members of the same file`() {
    myFixture.testHighlighting("NoWarningsMembersOfTheSameFile.kt")
  }

  fun `test kotlin unstable api usages`() {
    inspection.myIgnoreInsideImports = false
    myFixture.testHighlighting("UnstableElementsTest.kt")
  }

  fun `test kotlin do not report unstable api usages inside import statements`() {
    inspection.myIgnoreInsideImports = true
    myFixture.testHighlighting("UnstableElementsIgnoreImportsTest.kt")
  }
}

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/unstableApiUsage/scheduledForRemoval")
class ScheduledForRemovalApiUsageTest: JavaCodeInsightFixtureTestCase() {

  private val inspection = UnstableApiUsageInspection()

  override fun getBasePath() = "${JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH}/codeInspection/unstableApiUsage/scheduledForRemoval"

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
      "pkg/NonAnnotatedEnum.java",
      "pkg/ClassWithScheduledForRemovalTypeInSignature.java",
      "pkg/OwnerOfMembersWithScheduledForRemovalTypesInSignature.java"
    ).forEach { myFixture.copyFileToProject(it) }
  }

  fun testKotlinInspection() {
    inspection.myIgnoreInsideImports = false
    myFixture.testHighlighting("ScheduledForRemovalElementsTest.kt")
  }

  fun testJavaInspection() {
    inspection.myIgnoreInsideImports = false
    myFixture.testHighlighting(true, false, false, "ScheduledForRemovalElementsTest.java")
  }
}