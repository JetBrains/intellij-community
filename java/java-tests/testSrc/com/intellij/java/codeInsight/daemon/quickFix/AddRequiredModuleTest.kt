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
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*

/**
 * @author Pavel.Dolgov
 */
class AddRequiredModuleTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  val messageM2 = QuickFixBundle.message("module.info.add.requires.name", "M2")!!

  override fun setUp() {
    super.setUp()
    addFile("module-info.java", "module M2 { exports pkgA; }", M2)
    addFile("pkgA/A.java", "package pkgA; public class A {}", M2)
  }

  fun testAddRequiresToModuleInfo() {
    addFile("module-info.java", "module MAIN {}", MAIN)
    val editedFile = addFile("pkgB/B.java", "package pkgB; " +
        "import <caret>pkgA.A; " +
        "public class B { A a; }", MAIN)
    myFixture.configureFromExistingVirtualFile(editedFile)

    val action = myFixture.findSingleIntention(messageM2)
    assertNotNull(action)
    myFixture.launchAction(action)

    myFixture.checkHighlighting()  // error is gone
    myFixture.checkResult("module-info.java", "module MAIN {\n    requires M2;\n}", false)
  }

  fun testNoIdeaModuleDependency() {
    addFile("module-info.java", "module M3 {}", M3)
    val editedFile = addFile("pkgB/B.java", "package pkgB; " +
        "import <caret>pkgA.A; " +
        "public class B { A a; }", M3)
    myFixture.configureFromExistingVirtualFile(editedFile)

    val actions = myFixture.filterAvailableIntentions(messageM2)
    assertEmpty(actions)
  }
}