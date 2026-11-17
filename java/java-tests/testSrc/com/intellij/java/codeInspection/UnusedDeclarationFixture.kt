// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.JavaInspectionFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.javaInspectionFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import java.util.function.BiFunction

/**
 * The fixture of the unused declaration tests. Create it with [unusedDeclarationFixture].
 *
 * Each test gets a new [tool] in [toolWrapper]. A test changes the options of [tool] before it calls [doTest].
 */
@TestOnly
class UnusedDeclarationFixture internal constructor(
  /**
   * The inspection fixture of the test.
   */
  val inspection: JavaInspectionFixture,
) {
  val toolWrapper: GlobalInspectionToolWrapper = JavaInspectionFixture.getUnusedDeclarationWrapper()

  val tool: UnusedDeclarationInspection = toolWrapper.tool as UnusedDeclarationInspection

  /**
   * Returns the name of the test method without the `test` prefix, see [JavaInspectionFixture.getTestName].
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = inspection.getTestName(lowercaseFirstLetter)

  /**
   * Turns off the reports of the unused local variables and parameters,
   * and runs [tool] on the test data directory `deadCode/<test name>`.
   */
  fun doTest() {
    tool.sharedLocalInspectionTool.LOCAL_VARIABLE = false
    tool.sharedLocalInspectionTool.PARAMETER = false
    doTest("deadCode/" + getTestName(true), toolWrapper)
  }

  /**
   * Runs [toolWrapper] on the test data directory [folderName], see [JavaInspectionFixture.doTest].
   */
  fun doTest(folderName: String, toolWrapper: InspectionToolWrapper<*, *>) {
    inspection.doTest(folderName, toolWrapper)
  }
}

/**
 * Creates an [UnusedDeclarationFixture] for one test. Declare it as an instance field.
 *
 * [testDataPath] is the absolute test data path, by default the `inspection` directory of the Java test data.
 * [analysisScope] gives the scope of the inspection, see [javaInspectionFixture].
 */
@TestOnly
@JvmOverloads
fun unusedDeclarationFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String = JavaTestUtil.getJavaTestDataPath() + "/inspection",
  analysisScope: BiFunction<Project, VirtualFile, AnalysisScope>? = null,
): TestFixture<UnusedDeclarationFixture> {
  val inspectionFixture = javaInspectionFixture(projectFixture, testDataPath, analysisScope)
  return testFixture("unusedDeclarationFixture") {
    initialized(UnusedDeclarationFixture(inspectionFixture.init())) {}
  }
}
