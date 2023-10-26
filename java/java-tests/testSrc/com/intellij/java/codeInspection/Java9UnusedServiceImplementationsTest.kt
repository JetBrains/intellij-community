// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.codeInspection.java19modules.Java9ModuleEntryPoint
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.JavaInspectionTestCase
import org.intellij.lang.annotations.Language

class Java9UnusedServiceImplementationsTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun getTestDataPath() = PathManagerEx.getTestDataPath() + "/inspection/unusedServiceImplementations/"

  override fun setUp() {
    super.setUp()

    moduleInfo("module MAIN { requires API; }", MAIN)

    addFile("my/api/MyService.java", "package my.api; public interface MyService { void foo(); }", API)
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

  fun testUnusedImplementationWithEntryPoint() = doTest(false, withModuleEntryPoints = true)

  fun testUnusedConstructorWithEntryPoint() = doTest(false, withModuleEntryPoints = true)

  fun testUnusedProviderWithEntryPoint() = doTest(false, withModuleEntryPoints = true)


  private fun doTest(withUsage: Boolean = true, sameModule: Boolean = true, withModuleEntryPoints: Boolean = false) {
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
      moduleInfo("module API { exports my.api; provides my.api.MyService with my.impl.MyServiceImpl; }", API)
    }
    else {
      moduleInfo("module API { exports my.api; }", API)
      moduleInfo("module EXT { requires API; provides my.api.MyService with my.ext.MyServiceExt; }", EXT)
    }

    val testPath = testDataPath + getTestName(true)
    val sourceFile = FileUtil.findFirstThatExist("$testPath/MyService${if (sameModule) "Impl" else "Ext"}.java")
    assertNotNull("Test data: $testPath", sourceFile)
    val implText = String(FileUtil.loadFileText(sourceFile!!))
    if (sameModule)
      addFile("my/impl/MyServiceImpl.java", implText, API)
    else
      addFile("my/ext/MyServiceExt.java", implText, EXT)

    val moduleEntryPoint = EntryPointsManagerBase.DEAD_CODE_EP_NAME.extensionList.find { it is Java9ModuleEntryPoint }
    val wasSelected = moduleEntryPoint?.isSelected ?: true

    try {
      moduleEntryPoint?.isSelected = withModuleEntryPoints
      doGlobalInspectionTest(testPath, JavaInspectionTestCase.getUnusedDeclarationWrapper())
    }
    finally {
      moduleEntryPoint?.isSelected = wasSelected
    }
  }

  private val API = M7
  private val EXT = M6
}