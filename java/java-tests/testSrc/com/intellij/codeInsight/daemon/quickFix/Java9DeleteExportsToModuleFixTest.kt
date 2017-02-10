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
package com.intellij.codeInsight.daemon.quickFix

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.java19modules.Java9ModuleExportsPackageToItselfInspection
import com.intellij.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NonNls

/**
 * @author Pavel.Dolgov
 */
class Java9DeleteExportsToModuleFixTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  val message = InspectionsBundle.message("exports.to.itself.delete.module.fix.name", "M")!!

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(Java9ModuleExportsPackageToItselfInspection())

    addFile("module-info.java", "module M2 { }", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2)
    addFile("module-info.java", "module M4 { }", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M4)
    addFile("pkg/main/C.java", "package pkg.main; public class C {}")
  }

  fun testOnlySelfModule() {
    doFix("module M { exports pkg.main to <caret>M; }",
          "module M { exports pkg.main; }")
  }

  fun testOnlySelfModuleWithComments() {
    doFix("module M { exports pkg.main to /*a*/ <caret>M /*b*/; }",
          "module M { exports pkg.main  /*a*/  /*b*/; }")
  }

  fun testSelfModuleInList() {
    doFix("module M { exports pkg.main to M2, <caret>M , M4; }",
          "module M { exports pkg.main to M2, M4; }")
  }

  fun testSelfModuleInListWithComments() {
    doFix("module M { exports pkg.main to M2, /*a*/ <caret>M /*b*/,/*c*/ M4; }",
          "module M { exports pkg.main to M2, /*a*/  /*b*//*c*/ M4; }")
  }

  private fun doFix(textBefore: String, @Language("JAVA") @NonNls textAfter: String) {
    myFixture.configureByText("module-info.java", textBefore)

    val action = myFixture.findSingleIntention(message)
    assertNotNull(action)
    myFixture.launchAction(action)

    myFixture.checkHighlighting()  // no warning
    myFixture.checkResult("module-info.java", textAfter, false)
  }
}
