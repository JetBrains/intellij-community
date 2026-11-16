// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.tests.kotlin

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.java19modules.Java9RedundantRequiresStatementInspection
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Duration.Companion.minutes

@TestApplication
abstract class KotlinRedundantRequiresStatementTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(
    projectFixture,
    PathManager.getCommunityHomePath().replace(File.separatorChar, '/') +
    KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/redundantRequires/",
  )
  private val myFixture get() = java9.fixture

  @Test
  fun testStdlib(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val mainText = """
        package org.example.main
        class Main {
          fun main() {}
        }""".trimIndent()
    java9.addFile("org.example.main/Main.kt", mainText)
    java9.addFile("module-info.java", "module MAIN { requires kotlin.stdlib; }", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN)

    val toolWrapper = GlobalInspectionToolWrapper(Java9RedundantRequiresStatementInspection())
    val scope = AnalysisScope(myFixture.project)
    val globalContext = createGlobalContextForTool(scope, myFixture.project, listOf(toolWrapper))
    withContext(Dispatchers.EDT) {
      InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
      InspectionTestUtil.compareToolResults(globalContext, toolWrapper, true, myFixture.testDataPath + myFixture.getTestName(true))
    }
  }
}