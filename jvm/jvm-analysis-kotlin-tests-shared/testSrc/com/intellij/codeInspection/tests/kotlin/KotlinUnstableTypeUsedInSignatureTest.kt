package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.UnstableTypeUsedInSignatureInspection
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import java.io.File

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/unstableTypeUsedInSignature")
abstract class KotlinUnstableTypeUsedInSignatureTest : JavaCodeInsightFixtureTestCase(), KotlinPluginModeProvider {

  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/unstableTypeUsedInSignature"

  override fun getTestDataPath(): String = PathManager.getCommunityHomePath().replace(File.separatorChar, '/') + basePath


  override fun setUp() {
    super.setUp()
    val inspection = UnstableTypeUsedInSignatureInspection()
    inspection.unstableApiAnnotations.clear()
    inspection.unstableApiAnnotations.add(ApiStatus.Experimental::class.java.canonicalName)
    myFixture.enableInspections(inspection)
    configureAnnotatedFiles()
  }

  private fun configureAnnotatedFiles() {
    listOf(
      "experimentalPackage/ClassInExperimentalPackage.java",
      "experimentalPackage/package-info.java",
      "experimentalPackage/NoWarnings.java",
      "test/ExperimentalClass.java"
    ).forEach { myFixture.copyFileToProject(it) }
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(ApiStatus.Experimental::class.java))
  }

  fun `test kotlin missing unstable annotation`() {
    myFixture.testHighlighting("UnstableTypeUsedInSignature.kt")
    myFixture.testHighlighting("UnstableTypeUsedInTypeParameter.kt")
  }

  fun `test java missing unstable annotation`() {
    myFixture.testHighlighting("UnstableTypeUsedInSignature.java")
    myFixture.testHighlighting("UnstableTypeUsedInTypeParameter.java")
  }

  fun `test java no extra warnings are produced`() {
    myFixture.testHighlighting("noWarnings/NoWarningsAlreadyMarked.java")
    myFixture.testHighlighting("noWarnings/NoWarningsInaccessible.java")
    myFixture.testHighlighting("noWarnings/NoWarningsMembersAlreadyMarked.java")
  }

  fun `test kotlin no extra warnings are produced`() {
    myFixture.testHighlighting("noWarnings/NoWarningsAlreadyMarked.kt")
    myFixture.testHighlighting("noWarnings/NoWarningsInaccessible.kt")
  }

  fun `test no warnings produced in experimental package`() {
    myFixture.testHighlighting("experimentalPackage/NoWarnings.java")
  }

}