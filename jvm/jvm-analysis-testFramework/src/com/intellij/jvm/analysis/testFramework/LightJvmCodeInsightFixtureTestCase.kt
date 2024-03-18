package com.intellij.jvm.analysis.testFramework

import com.intellij.openapi.application.PathManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.io.File

abstract class LightJvmCodeInsightFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {
  override fun getTestDataPath(): String = PathManager.getCommunityHomePath().replace(File.separatorChar, '/') + basePath

  override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptor(LanguageLevel.HIGHEST)

  protected fun generateFileName() = getTestName(false).replace("[^a-zA-Z0-9\\.\\-]", "_")
}