package com.intellij.jvm.analysis.testFramework

import com.intellij.openapi.application.PathManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.io.File

/**
 * @deprecated Use [jvmProjectFixture] and [jvmCodeInsightFixture] in a JUnit 5 test.
 */
@Deprecated("Use jvmProjectFixture and jvmCodeInsightFixture in a JUnit 5 test")
abstract class LightJvmCodeInsightFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {
  override fun getTestDataPath(): String = PathManager.getCommunityHomePath().replace(File.separatorChar, '/') + basePath

  override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptor(LanguageLevel.HIGHEST)

  protected fun generateFileName(): String = getTestName(false).replace("[^a-zA-Z0-9\\.\\-]", "_")
}