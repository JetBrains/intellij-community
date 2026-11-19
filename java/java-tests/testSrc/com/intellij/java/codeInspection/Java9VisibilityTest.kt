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
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.java19modules.Java9ModuleEntryPoint
import com.intellij.codeInspection.visibility.VisibilityInspection
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class Java9VisibilityTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/java9Visibility/")
  private val myFixture get() = java9.fixture

  @Test
  fun testPublicClass(): Unit = doTestClass()
  @Test
  fun testPublicInnerClass(): Unit = doTestClass()
  @Test
  fun testProtectedNestedClass(): Unit = doTestClass()

  @Test
  fun testInheritedService(): Unit = doTestService()
  @Test
  fun testProvidedService(): Unit = doTestService()

  @Test
  fun testUsedService() {
    java9.moduleInfo("module foo.bar { exports foo.bar; uses foo.bar.Api; uses foo.bar.impl.Impl; }")
    doTest("foo.bar.Api", "foo.bar.impl.Impl", "foo.bar.impl.Other")
  }

  @Test
  fun testReduceVisibilityInExportedPackages() {
    java9.moduleInfo("""module foo.bar {
  exports foo.bar;
  provides foo.bar.ServiceApi with foo.bar.ServiceImpl;
  uses foo.bar.UsedService;
}""")
    doTest("foo.bar.Public", "foo.bar.ServiceApi", "foo.bar.ServiceImpl", "foo.bar.UsedService",
           reduceVisibilityInExportedPackages = true)
  }

  private fun doTestClass() {
    java9.moduleInfo("module foo.bar { exports foo.bar; }")
    doTest("foo.bar.Api", "foo.bar.impl.Impl")
  }

  private fun doTestService() {
    java9.moduleInfo("module foo.bar { exports foo.bar; provides foo.bar.Api with foo.bar.impl.Impl; }")
    doTest("foo.bar.Api", "foo.bar.impl.Impl", "foo.bar.impl.Other")
  }

  private fun doTest(vararg classNames: String, reduceVisibilityInExportedPackages: Boolean = false) {
    val testPath = myFixture.testDataPath + myFixture.getTestName(true)
    java9.addJavaFiles(testPath, classNames)

    val inspection = VisibilityInspection()
    inspection.setEntryPointEnabled(Java9ModuleEntryPoint.ID, reduceVisibilityInExportedPackages)

    val toolWrapper = GlobalInspectionToolWrapper(inspection)
    java9.doGlobalInspectionTest(testPath, toolWrapper)
  }
}
