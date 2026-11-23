// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.codeInspection.java19modules.Java9ModuleEntryPoint
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M6
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M7
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.JavaInspectionFixture
import com.intellij.testFramework.junit5.TestApplication
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class Java9UnusedServiceImplementationsTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/unusedServiceImplementations/")
  private val myFixture get() = java9.fixture

  @BeforeEach
  fun setUp() {
    java9.moduleInfo("module MAIN { requires API; }", MAIN)

    java9.addFile("my/api/MyService.java", "package my.api; public interface MyService { void foo(); }", API)
  }

  @Test
  fun testImplementation(): Unit = doTest()

  @Test
  fun testConstructor(): Unit = doTest()

  @Test
  fun testProvider(): Unit = doTest()

  @Test
  fun testVarargConstructor(): Unit = doTest()

  @Test
  fun testUnusedImplementation(): Unit = doTest(false)

  @Test
  fun testUnusedConstructor(): Unit = doTest(false)

  @Test
  fun testUnusedProvider(): Unit = doTest(false)

  @Test
  fun testUnusedVarargConstructor(): Unit = doTest(false)

  @Test
  fun testExternalImplementation(): Unit = doTest(sameModule = false)

  @Test
  fun testExternalConstructor(): Unit = doTest(sameModule = false)

  @Test
  fun testExternalProvider(): Unit = doTest(sameModule = false)

  @Test
  fun testUnusedExternalImplementation(): Unit = doTest(false, sameModule = false)

  @Test
  fun testUnusedExternalConstructor(): Unit = doTest(false, sameModule = false)

  @Test
  fun testUnusedExternalProvider(): Unit = doTest(false, sameModule = false)

  @Test
  fun testUnusedImplementationWithEntryPoint(): Unit = doTest(false, withModuleEntryPoints = true)

  @Test
  fun testUnusedConstructorWithEntryPoint(): Unit = doTest(false, withModuleEntryPoints = true)

  @Test
  fun testUnusedProviderWithEntryPoint(): Unit = doTest(false, withModuleEntryPoints = true)

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
    if (withUsage) java9.addFile("my/app/MyApp.java", usageText, MAIN)

    if (sameModule) {
      java9.moduleInfo("module API { exports my.api; provides my.api.MyService with my.impl.MyServiceImpl; }", API)
    }
    else {
      java9.moduleInfo("module API { exports my.api; }", API)
      java9.moduleInfo("module EXT { requires API; provides my.api.MyService with my.ext.MyServiceExt; }", EXT)
    }

    val testPath = myFixture.testDataPath + myFixture.getTestName(true)
    val sourceFile = FileUtil.findFirstThatExist("$testPath/MyService${if (sameModule) "Impl" else "Ext"}.java")
    assertNotNull(sourceFile, "Test data: $testPath")
    val implText = String(FileUtil.loadFileText(sourceFile!!))
    if (sameModule)
      java9.addFile("my/impl/MyServiceImpl.java", implText, API)
    else
      java9.addFile("my/ext/MyServiceExt.java", implText, EXT)

    val moduleEntryPoint = EntryPointsManagerBase.DEAD_CODE_EP_NAME.extensionList.find { it is Java9ModuleEntryPoint }
    val wasSelected = moduleEntryPoint?.isSelected ?: true

    try {
      moduleEntryPoint?.isSelected = withModuleEntryPoints
      java9.doGlobalInspectionTest(testPath, JavaInspectionFixture.getUnusedDeclarationWrapper())
    }
    finally {
      moduleEntryPoint?.isSelected = wasSelected
    }
  }

  private val API = M7
  private val EXT = M6
}