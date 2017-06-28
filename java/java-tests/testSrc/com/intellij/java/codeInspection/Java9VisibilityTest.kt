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

import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.java19modules.Java9ModuleEntryPoint
import com.intellij.codeInspection.visibility.VisibilityInspection
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.openapi.application.ex.PathManagerEx

/**
 * @author Pavel.Dolgov
 */
class Java9VisibilityTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun getTestDataPath() = PathManagerEx.getTestDataPath() + "/inspection/java9Visibility/"

  fun testPublicClass() = doTestClass()
  fun testPublicInnerClass() = doTestClass()
  fun testProtectedNestedClass() = doTestClass()

  fun testInheritedService() = doTestService()
  fun testProvidedService() = doTestService()

  fun testUsedService() {
    moduleInfo("module foo.bar { exports foo.bar; uses foo.bar.Api; uses foo.bar.impl.Impl; }")
    doTest("foo.bar.Api", "foo.bar.impl.Impl", "foo.bar.impl.Other")
  }

  fun testReduceVisibilityInExportedPackages() {
    moduleInfo("""module foo.bar {
  exports foo.bar;
  provides foo.bar.ServiceApi with foo.bar.ServiceImpl;
  uses foo.bar.UsedService;
}""")
    doTest("foo.bar.Public", "foo.bar.ServiceApi", "foo.bar.ServiceImpl", "foo.bar.UsedService",
           reduceVisibilityInExportedPackages = true)
  }

  private fun doTestClass() {
    moduleInfo("module foo.bar { exports foo.bar; }")
    doTest("foo.bar.Api", "foo.bar.impl.Impl")
  }

  private fun doTestService() {
    moduleInfo("module foo.bar { exports foo.bar; provides foo.bar.Api with foo.bar.impl.Impl; }")
    doTest("foo.bar.Api", "foo.bar.impl.Impl", "foo.bar.impl.Other")
  }

  private fun doTest(vararg classNames: String, reduceVisibilityInExportedPackages: Boolean = false) {
    val testPath = testDataPath + getTestName(true)
    addJavaFiles(testPath, classNames)

    val inspection = VisibilityInspection()
    inspection.setEntryPointEnabled(Java9ModuleEntryPoint.ID, reduceVisibilityInExportedPackages)

    val toolWrapper = GlobalInspectionToolWrapper(inspection)
    doGlobalInspectionTest(testPath, toolWrapper)
  }
}
