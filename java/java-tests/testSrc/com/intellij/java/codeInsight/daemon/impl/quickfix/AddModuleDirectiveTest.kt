// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.impl.quickfix.AddExportsDirectiveFix
import com.intellij.codeInsight.daemon.impl.quickfix.AddRequiresDirectiveFix
import com.intellij.codeInsight.daemon.impl.quickfix.AddUsesDirectiveFix
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.modcommand.ModCommandAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaModule
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class AddModuleDirectiveTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture)
  private val myFixture get() = java9.fixture

  @Test
  fun testNewRequires(): Unit = doRequiresTest(
    "module M { }",
    "module M {\n" +
    "    requires M2;\n" +
    "}")

  @Test
  fun testBrokenLeftBrace(): Unit = doRequiresTest(
    "module M }",
    "module M {\n" +
    "    requires M2; }")

  @Test
  fun testBrokenRightBrace(): Unit = doRequiresTest(
    "module M {",
    "module M {\n" +
    "    requires M2;")

  @Test
  fun testWithoutBraces(): Unit = doRequiresTest(
    "module M",
    "module M {\n" +
    "    requires M2;\n" +
    "}")

  @Test
  fun testRequiresAfterOther(): Unit = doRequiresTest(
    "module M {\n" +
    "    requires other;\n" +
    "}",
    "module M {\n" +
    "    requires other;\n" +
    "    requires M2;\n" +
    "}")

  @Test
  fun testNoDuplicateRequires(): Unit = doRequiresTest(
    "module M { requires M2; }",
    "module M { requires M2; }")

  @Test
  fun testNoCircularDependency() {
    java9.addFile("module-info.java", "module M2 { requires M; }", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2)
    doRequiresTest("module M { }", "module M { }")
  }

  @Test
  fun testRequiresInIncompleteModule(): Unit = doRequiresTest(
    "module M {",
    "module M {\n" +
    "    requires M2;")

  @Test
  fun testNewExports(): Unit = doExportsTest(
    "module M { }",
    "module M {\n" +
    "    exports pkg.m;\n" +
    "}")

  @Test
  fun testExportsAfterOther(): Unit = doExportsTest(
    "module M {\n" +
    "    exports pkg.other;\n" +
    "}",
    "module M {\n" +
    "    exports pkg.other;\n" +
    "    exports pkg.m;\n" +
    "}")

  @Test
  fun testNoNarrowingExports(): Unit = doExportsTest(
    "module M { exports pkg.m; }",
    "module M { exports pkg.m; }")

  @Test
  fun testNoDuplicateExports(): Unit = doExportsTest(
    "module M { exports pkg.m to M1, M2; }",
    "module M { exports pkg.m to M1, M2; }")

  @Test
  fun testNoExportsToUnnamed(): Unit = doExportsTest(
    "module M { exports pkg.m to M1; }",
    "module M { exports pkg.m to M1; }",
    target = "")

  @Test
  fun testExportsExtendsOther(): Unit = doExportsTest(
    "module M {\n" +
    "    exports pkg.m to M1;\n" +
    "}",
    "module M {\n" +
    "    exports pkg.m to M1, M2;\n" +
    "}")

  @Test
  fun testExportsExtendsIncompleteOther(): Unit = doExportsTest(
    "module M {\n" +
    "    exports pkg.m to M1\n" +
    "}",
    "module M {\n" +
    "    exports pkg.m to M1, M2\n" +
    "}")

  @Test
  fun testNewUses(): Unit = doUsesTest(
    "module M { }",
    "module M {\n" +
    "    uses pkg.m.C;\n" +
    "}")

  @Test
  fun testUsesAfterOther(): Unit = doUsesTest(
    "module M {\n" +
    "    uses pkg.m.B;\n" +
    "}",
    "module M {\n" +
    "    uses pkg.m.B;\n" +
    "    uses pkg.m.C;\n" +
    "}")

  @Test
  fun testNoDuplicateUses(): Unit = doUsesTest(
    "module M { uses pkg.m.C; }",
    "module M { uses pkg.m.C; }")

  private fun doRequiresTest(text: String, expected: String) = doTest(text, { AddRequiresDirectiveFix(it, "M2") }, expected)
  private fun doExportsTest(text: String, expected: String, target: String = "M2") = doTest(text, { AddExportsDirectiveFix(it, "pkg.m", target) }, expected)
  private fun doUsesTest(text: String, expected: String) = doTest(text, { AddUsesDirectiveFix(it, "pkg.m.C") }, expected)

  private fun doTest(text: String, fix: (PsiJavaModule) -> ModCommandAction, expected: String): Unit = timeoutRunBlocking {
    val file = myFixture.configureByText("module-info.java", text) as PsiJavaFile
    withContext(Dispatchers.EDT) {
      val action = fix(file.moduleDeclaration!!).asIntention()
      CommandProcessor.getInstance().executeCommand(myFixture.project, { action.invoke(myFixture.project, myFixture.editor, file) }, null, null)
      assertEquals(expected, file.text)
    }
  }
}