/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInspection.java19modules.Java9ModuleExportsPackageToItselfInspection
import com.intellij.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor

/**
 * @author Pavel.Dolgov
 */
class Java9ModuleExportsPackageToItselfTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(Java9ModuleExportsPackageToItselfInspection())

    addFile("module-info.java", "module M2 { }", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2)
    addFile("module-info.java", "module M4 { }", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M4)
    addFile("pkg/main/C.java", "package pkg.main; public class C {}")
  }

  fun testNoSelfModule() {
    highlight("module M { exports pkg.main to M2, M4; }")
  }

  fun testOnlySelfModule() {
    val message = InspectionsBundle.message("inspection.module.exports.package.to.itself.message")
    highlight("module M { exports pkg.main to <warning descr=\"$message\">M</warning>; }")
  }

  fun testSelfModuleInList() {
    val message = InspectionsBundle.message("inspection.module.exports.package.to.itself.message")
    highlight("module M { exports pkg.main to M2, <warning descr=\"$message\">M</warning>, M4; }")
  }

  private fun highlight(text: String) = highlight("module-info.java", text)

  private fun highlight(path: String, text: String) {
    myFixture.configureFromExistingVirtualFile(addFile(path, text))
    myFixture.checkHighlighting()
  }
}