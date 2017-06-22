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
package com.intellij.java.codeInspection

import com.intellij.ToolExtensionPoints
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.java19modules.Java9ModuleEntryPoint
import com.intellij.codeInspection.reference.EntryPoint
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.InspectionTestCase
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import org.intellij.lang.annotations.Language

/**
 * @author Pavel.Dolgov
 */
class Java9UnusedServiceImplementationsTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun getTestDataPath() = PathManagerEx.getTestDataPath() + "/inspection/unusedServiceImplementations/"

  override fun setUp() {
    super.setUp()

    moduleInfo("module MAIN { requires API; }", MAIN)

    addFile("my/api/MyService.java", "package my.api; public interface MyService { void foo(); }", M2)
  }

  fun testImplementation() = doTest()

  fun testConstructor() = doTest()

  fun testProvider() = doTest()

  fun testVarargConstructor() = doTest()

  fun testUnusedImplementation() = doTest(false)

  fun testUnusedConstructor() = doTest(false)

  fun testUnusedProvider() = doTest(false)

  fun testUnusedVarargConstructor() = doTest(false)

  fun testExternalImplementation() = doTest(sameModule = false)

  fun testExternalConstructor() = doTest(sameModule = false)

  fun testExternalProvider() = doTest(sameModule = false)

  fun testUnusedExternalImplementation() = doTest(false, sameModule = false)

  fun testUnusedExternalConstructor() = doTest(false, sameModule = false)

  fun testUnusedExternalProvider() = doTest(false, sameModule = false)

  fun testUnusedImplementationWithEntryPoint() = doTest(false, moduleEntryPoints = true)

  fun testUnusedConstructorWithEntryPoint() = doTest(false, moduleEntryPoints = true)

  fun testUnusedProviderWithEntryPoint() = doTest(false, moduleEntryPoints = true)


  private fun doTest(withUsage: Boolean = true, sameModule: Boolean = true, moduleEntryPoints: Boolean = false) {
    @Language("JAVA")
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

    if (sameModule) {
      moduleInfo("module API { exports my.api; provides my.api.MyService with my.impl.MyServiceImpl; }", M2)
    }
    else {
      val moduleManager = ModuleManager.getInstance(project)
      val m2 = moduleManager.findModuleByName(M2.moduleName)!!
      val m4 = moduleManager.findModuleByName(M4.moduleName)!!
      ModuleRootModificationUtil.addDependency(m4, m2)
      moduleInfo("module API { exports my.api; }", M2)
      moduleInfo("module EXT { requires API; provides my.api.MyService with my.ext.MyServiceExt; }", M4)
    }

    val testPath = testDataPath + "/" + getTestName(true)
    val sourceFile = FileUtil.findFirstThatExist("$testPath/MyService${if (sameModule) "Impl" else "Ext"}.java")
    assertNotNull("Test data: $testPath", sourceFile)
    val implText = String(FileUtil.loadFileText(sourceFile!!))
    if (sameModule)
      addFile("my/impl/MyServiceImpl.java", implText, M2)
    else
      addFile("my/ext/MyServiceExt.java", implText, M4)

    val point = Extensions.getRootArea().getExtensionPoint<EntryPoint>(ToolExtensionPoints.DEAD_CODE_TOOL)
    point.extensions.find { it is Java9ModuleEntryPoint }?.let { it.isSelected = moduleEntryPoints }

    val toolWrapper = InspectionTestCase.getUnusedDeclarationWrapper()
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    InspectionTestUtil.compareToolResults(globalContext, toolWrapper, true, testPath)
  }

  private fun moduleInfo(@Language("JAVA") moduleInfoText: String, descriptor: ModuleDescriptor) {
    addFile("module-info.java", moduleInfoText, descriptor)
  }
}