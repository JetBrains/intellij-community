// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.tests.kotlin

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.java19modules.Java9RedundantRequiresStatementInspection
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import java.io.File

abstract class KotlinRedundantRequiresStatementTest : LightJava9ModulesCodeInsightFixtureTestCase(), KotlinPluginModeProvider {
  fun testStdlib() {
    val mainText = """
        package org.example.main
        class Main {
          fun main() {}
        }""".trimIndent()
    addFile("org.example.main/Main.kt", mainText)
    addFile("module-info.java", "module MAIN { requires kotlin.stdlib; }", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN)

    val toolWrapper = GlobalInspectionToolWrapper(Java9RedundantRequiresStatementInspection())
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    InspectionTestUtil.compareToolResults(globalContext, toolWrapper, true, testDataPath + getTestName(true))
  }

  override fun getBasePath(): String {
    return KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/redundantRequires/"
  }

  override fun getTestDataPath(): String {
    return PathManager.getCommunityHomePath().replace(File.separatorChar, '/') + basePath
  }
}