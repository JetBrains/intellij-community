/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.idea.Bombed
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.loadFileText
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NonNls

/**
 * @author Pavel.Dolgov
 */
class Java9UnusedServiceImplementationsTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun getTestDataPath() = PathManagerEx.getTestDataPath() + "/inspection/unusedServiceImplementations/"

  override fun setUp() {
    super.setUp()

    addFile("module-info.java", "module MAIN { requires M2; }", MAIN)
    addFile("module-info.java", "module M2 { exports my.api; provides my.api.MyService with my.impl.MyServiceImpl; }", M2)

    addFile("my/api/MyService.java", "package my.api; public interface MyService { void foo(); }", M2)
  }

  fun testImplementation() = doTest()

  fun testConstructor() = doTest()

  fun testProvider() = doTest()

  @Bombed(user ="Pavel Dolgov", year = 2017, month = 8, day = 1, description = "Disabled, until the test infrastructure supports it")
  fun testUnusedImplementation() = doTest(false)

  @Bombed(user ="Pavel Dolgov", year = 2017, month = 8, day = 1, description = "Disabled, until the test infrastructure supports it")
  fun testUnusedConstructor() = doTest(false)

  @Bombed(user ="Pavel Dolgov", year = 2017, month = 8, day = 1, description = "Disabled, until the test infrastructure supports it")
  fun testUnusedProvider() = doTest(false)

  private fun doTest(withUsage: Boolean = true) {
    @Language("JAVA") @NonNls
    val usageText = """
    import my.api.MyService;
    public class MyApp {
        public static void main(String[] args) {
            for (MyService service : ServiceLoader.load(MyService.class)) {
                service.foo();
            }
        }
    }"""
    if (withUsage) addFile("my/app/MyApp.java", usageText, MAIN)

    val testPath = testDataPath + "/" + getTestName(true)
    val sourceFile = FileUtil.findFirstThatExist(testPath + "/MyServiceImpl.java")
    assertNotNull("Test data: $testPath", sourceFile)
    val implText = String(loadFileText(sourceFile!!))
    addFile("my/impl/MyServiceImpl.java", implText, M2)

    val toolWrapper = GlobalInspectionToolWrapper(UnusedDeclarationInspection())
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    InspectionTestUtil.compareToolResults(globalContext, toolWrapper, true, testPath)
  }
}