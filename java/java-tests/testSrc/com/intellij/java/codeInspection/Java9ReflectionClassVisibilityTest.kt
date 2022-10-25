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
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN
import com.intellij.openapi.util.io.FileUtil

/**
 * @author Pavel.Dolgov
 */
class Java9ReflectionClassVisibilityTest : LightJava9ModulesCodeInsightFixtureTestCase() {

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/java9ReflectionClassVisibility"

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(Java9ReflectionClassVisibilityInspection())
  }

  fun testOpenModule() {
    moduleInfo("module MAIN { requires API; }", MAIN)
    moduleInfo("open module API { }", M2)
    doTestHighlighting()
  }

  fun testOpensPackage() {
    moduleInfo("module MAIN { requires API; }", MAIN)
    moduleInfo("module API { opens my.api; }", M2)
    doTestHighlighting()
  }

  fun testExportsPackage() {
    moduleInfo("module MAIN { requires API; }", MAIN)
    moduleInfo("module API { exports my.api; }", M2)
    javaClass("my.api", "PackageLocal", M2, "")
    doTestHighlighting()
  }

  fun testExportsPackagePreview() {
    moduleInfo("module MAIN { requires API; }", MAIN)
    moduleInfo("module API { exports my.api; }", M2)
    doTestPreview("Add 'exports my.impl' directive to module-info.java",
                  "module API { exports my.api;\n    exports my.impl;\n}")
  }

  fun testNotInRequirements() {
    moduleInfo("module MAIN { }", MAIN)
    moduleInfo("open module API { }", M2)
    doTestHighlighting()
  }

  private fun configureMainFile() {
    val testPath = "$testDataPath/${getTestName(false)}.java"
    val mainFile = FileUtil.findFirstThatExist(testPath)
    assertNotNull("Test data: $testPath", mainFile)
    val mainText = String(FileUtil.loadFileText(mainFile!!))

    myFixture.configureFromExistingVirtualFile(addFile("my/main/Main.java", mainText, MAIN))
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
    addFile("${packageName.replace('.', '/')}/$className.java", "package $packageName; $modifier class $className {}", descriptor)
  }
}
