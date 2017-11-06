// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*

/**
 * @author Pavel.Dolgov
 */
class AddRequiredModuleTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  private val messageM2 = QuickFixBundle.message("module.info.add.requires.name", "M2")

  override fun setUp() {
    super.setUp()
    addFile("module-info.java", "module M2 { exports pkgA; }", M2)
    addFile("pkgA/A.java", "package pkgA; public class A {}", M2)
  }

  fun testAtTheBeginning() =
    doTest("module MAIN {}",
           "module MAIN {\n" +
           "    requires M2;\n" +
           "}")

  fun testAfterOtherRequires() =
    doTest("module MAIN {\n" +
           "    requires java.logging;\n" +
           "}",
           "module MAIN {\n" +
           "    requires java.logging;\n" +
           "    requires M2;\n" +
           "}")

  fun testIncompleteModuleInfo() =
    doTest("module MAIN {", "module MAIN {\n    requires M2;")

  private fun doTest(before: String, after: String) {
    addFile("module-info.java", before, MAIN)
    val editedFile = addFile("pkgB/B.java", "package pkgB; " +
        "import <caret>pkgA.A; " +
        "public class B { A a; }", MAIN)
    myFixture.configureFromExistingVirtualFile(editedFile)

    val action = myFixture.findSingleIntention(messageM2)
    assertNotNull(action)
    myFixture.launchAction(action)

    myFixture.checkHighlighting()  // error is gone
    myFixture.checkResult("module-info.java", after, false)
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