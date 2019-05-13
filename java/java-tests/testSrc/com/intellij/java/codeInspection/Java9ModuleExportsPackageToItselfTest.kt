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

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.java19modules.Java9ModuleExportsPackageToItselfInspection
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor
import org.intellij.lang.annotations.Language

/**
 * @author Pavel.Dolgov
 */
class Java9ModuleExportsPackageToItselfTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  private val message = InspectionsBundle.message("inspection.module.exports.package.to.itself")!!
  private val fix1 = InspectionsBundle.message("exports.to.itself.delete.statement.fix")!!
  private val fix2 = InspectionsBundle.message("exports.to.itself.delete.module.ref.fix", "M")!!

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(Java9ModuleExportsPackageToItselfInspection())

    addFile("module-info.java", "module M2 { }", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2)
    addFile("module-info.java", "module M4 { }", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M4)
    addFile("pkg/main/C.java", "package pkg.main; public class C {}")
  }

  fun testNoSelfModule() {
    highlight("module M {\n exports pkg.main to M2, M4;\n opens pkg.main to M2, M4;\n}")
  }

  fun testOnlySelfExport() {
    highlight("module M { exports pkg.main to <warning descr=\"$message\">M</warning>; }")
    fix("module M { exports pkg.main to <caret>M; }",
        "module M {\n}")
  }

  fun testOnlySelfOpen() {
    highlight("module M { opens pkg.main to <warning descr=\"$message\">M</warning>; }")
    fix("module M { opens pkg.main to <caret>M; }",
        "module M {\n}")
  }

  fun testOnlySelfModuleWithComments() {
    fix("module M { exports pkg.main to /*a*/ <caret>M /*b*/; }",
        "module M { /*a*/ /*b*/\n}")
  }

  fun testSelfModuleInList() {
    highlight("module M { exports pkg.main to M2, <warning descr=\"$message\">M</warning>, M4; }")
    fix("module M { exports pkg.main to M2, <caret>M , M4; }",
        "module M { exports pkg.main to M2, M4; }")
  }

  fun testSelfModuleInListWithComments() {
    fix("module M { exports pkg.main to M2, /*a*/ <caret>M /*b*/,/*c*/ M4; }",
        "module M { exports pkg.main to M2, /*a*/  /*b*//*c*/ M4; }")
  }

  private fun highlight(text: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.checkHighlighting()
  }

  private fun fix(textBefore: String, @Language("JAVA") textAfter: String) {
    myFixture.configureByText("module-info.java", textBefore)

    val action = myFixture.filterAvailableIntentions(fix1).firstOrNull() ?: myFixture.filterAvailableIntentions(fix2).first()
    myFixture.launchAction(action)

    myFixture.checkHighlighting()  // no warning
    myFixture.checkResult("module-info.java", textAfter, false)
  }
}