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

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.visibility.VisibilityInspection
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.LightProjectDescriptor

class Java9AccessCanBeTightenedTest : LightJava9ModulesCodeInsightFixtureTestCase() {

  override fun getTestDataPath() = PathManagerEx.getTestDataPath() + "/inspection/java9AccessCanBeTightened/"

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  fun testExportedPackage() {
    moduleInfo("module foo.bar { exports foo.bar; }")
    doTest("foo.bar.Api", "foo.bar.Outer")
  }

  fun testNotExportedPackage() {
    moduleInfo("module foo.bar { }")
    doTest("foo.bar.Api", "foo.bar.Outer")
  }

  fun testDeclaredService() {
    moduleInfo("module foo.bar { exports foo.bar; provides foo.bar.Api with foo.bar.impl.Impl; }")
    doTest("foo.bar.Api", "foo.bar.impl.Impl")
  }

  fun testNotDeclaredService() {
    moduleInfo("module foo.bar { exports foo.bar; }")
    doTest("foo.bar.Api", "foo.bar.impl.Impl")
  }


  private fun doTest(vararg classNames: String) {
    val testPath = testDataPath + getTestName(true)
    addJavaFiles(testPath, classNames)

    val toolWrapper = LocalInspectionToolWrapper(createGlobalTool().sharedLocalInspectionTool!!)
    doGlobalInspectionTest(testPath, toolWrapper)
  }

  private fun createGlobalTool() = VisibilityInspection().apply {
    SUGGEST_PRIVATE_FOR_INNERS = true
    SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true
    SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true
  }
}