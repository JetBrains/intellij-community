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

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.reflectiveAccess.Java9ReflectionClassVisibilityInspection
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class Java9ReflectionClassVisibilityTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/java9ReflectionClassVisibility")
  private val myFixture get() = java9.fixture

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(Java9ReflectionClassVisibilityInspection())
  }

  @Test
  fun testOpenModule() {
    java9.moduleInfo("module MAIN { requires API; }", MAIN)
    java9.moduleInfo("open module API { }", M2)
    doTestHighlighting()
  }

  @Test
  fun testOpensPackage() {
    java9.moduleInfo("module MAIN { requires API; }", MAIN)
    java9.moduleInfo("module API { opens my.api; }", M2)
    doTestHighlighting()
  }

  @Test
  fun testExportsPackage() {
    java9.moduleInfo("module MAIN { requires API; }", MAIN)
    java9.moduleInfo("module API { exports my.api; }", M2)
    javaClass("my.api", "PackageLocal", M2, "")
    doTestHighlighting()
  }

  @Test
  fun testExportsPackagePreview() {
    java9.moduleInfo("module MAIN { requires API; }", MAIN)
    java9.moduleInfo("module API { exports my.api; }", M2)
    doTestPreview("Add 'exports my.impl' directive to module-info.java",
                  "module API { exports my.api;\n    exports my.impl;\n}")
  }

  @Test
  fun testNotInRequirements() {
    java9.moduleInfo("module MAIN { }", MAIN)
    java9.moduleInfo("open module API { }", M2)
    doTestHighlighting()
  }

  private fun configureMainFile() {
    val testPath = "${myFixture.testDataPath}/${myFixture.getTestName(false)}.java"
    val mainFile = FileUtil.findFirstThatExist(testPath)
    assertNotNull(mainFile, "Test data: $testPath")
    val mainText = String(FileUtil.loadFileText(mainFile!!))

    myFixture.configureFromExistingVirtualFile(java9.addFile("my/main/Main.java", mainText, MAIN))
  }

  private fun doTestHighlighting() {
    javaClass("my.api", "Api", M2)
    javaClass("my.impl", "Impl", M2)
    configureMainFile()
    myFixture.checkHighlighting()
  }

  @Suppress("SameParameterValue")
  private fun doTestPreview(hint: String, expectedPreview: String) {
    javaClass("my.impl", "Impl", M2)
    configureMainFile()
    val actualPreview = myFixture.getIntentionPreviewText(myFixture.findSingleIntention(hint))
    assertEquals(expectedPreview, actualPreview)
  }

  private fun javaClass(packageName: String, className: String, descriptor: ModuleDescriptor, modifier: String = "public") {
    java9.addFile("${packageName.replace('.', '/')}/$className.java", "package $packageName; $modifier class $className {}", descriptor)
  }
}
