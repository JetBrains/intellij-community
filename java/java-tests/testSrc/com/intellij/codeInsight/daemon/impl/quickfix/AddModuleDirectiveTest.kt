// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.modcommand.ModCommandAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaModule

class AddModuleDirectiveTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  fun testNewRequires(): Unit = doRequiresTest(
    "module M { }",
    "module M {\n" +
    "    requires M2;\n" +
    "}")

  fun testRequiresAfterOther(): Unit = doRequiresTest(
    "module M {\n" +
    "    requires other;\n" +
    "}",
    "module M {\n" +
    "    requires other;\n" +
    "    requires M2;\n" +
    "}")

  fun testNoDuplicateRequires(): Unit = doRequiresTest(
    "module M { requires M2; }",
    "module M { requires M2; }")

  fun testRequiresInIncompleteModule(): Unit = doRequiresTest(
    "module M {",
    "module M {\n" +
    "    requires M2;")

  fun testNewExports(): Unit = doExportsTest(
    "module M { }",
    "module M {\n" +
    "    exports pkg.m;\n" +
    "}")

  fun testExportsAfterOther(): Unit = doExportsTest(
    "module M {\n" +
    "    exports pkg.other;\n" +
    "}",
    "module M {\n" +
    "    exports pkg.other;\n" +
    "    exports pkg.m;\n" +
    "}")

  fun testNoNarrowingExports(): Unit = doExportsTest(
    "module M { exports pkg.m; }",
    "module M { exports pkg.m; }")

  fun testNoDuplicateExports(): Unit = doExportsTest(
    "module M { exports pkg.m to M1, M2; }",
    "module M { exports pkg.m to M1, M2; }")

  fun testNoExportsToUnnamed(): Unit = doExportsTest(
    "module M { exports pkg.m to M1; }",
    "module M { exports pkg.m to M1; }",
    target = "")

  fun testExportsExtendsOther(): Unit = doExportsTest(
    "module M {\n" +
    "    exports pkg.m to M1;\n" +
    "}",
    "module M {\n" +
    "    exports pkg.m to M1, M2;\n" +
    "}")

  fun testExportsExtendsIncompleteOther(): Unit = doExportsTest(
    "module M {\n" +
    "    exports pkg.m to M1\n" +
    "}",
    "module M {\n" +
    "    exports pkg.m to M1, M2\n" +
    "}")

  fun testNewUses(): Unit = doUsesTest(
    "module M { }",
    "module M {\n" +
    "    uses pkg.m.C;\n" +
    "}")

  fun testUsesAfterOther(): Unit = doUsesTest(
    "module M {\n" +
    "    uses pkg.m.B;\n" +
    "}",
    "module M {\n" +
    "    uses pkg.m.B;\n" +
    "    uses pkg.m.C;\n" +
    "}")

  fun testNoDuplicateUses(): Unit = doUsesTest(
    "module M { uses pkg.m.C; }",
    "module M { uses pkg.m.C; }")

  private fun doRequiresTest(text: String, expected: String) = doTest(text, { AddRequiresDirectiveFix(it, "M2") }, expected)
  private fun doExportsTest(text: String, expected: String, target: String = "M2") = doTest(text, { AddExportsDirectiveFix(it, "pkg.m", target) }, expected)
  private fun doUsesTest(text: String, expected: String) = doTest(text, { AddUsesDirectiveFix(it, "pkg.m.C") }, expected)

  private fun doTest(text: String, fix: (PsiJavaModule) -> ModCommandAction, expected: String) {
    val file = myFixture.configureByText("module-info.java", text) as PsiJavaFile
    val action = fix(file.moduleDeclaration!!).asIntention()
    CommandProcessor.getInstance().executeCommand(project, { action.invoke(project, editor, file) }, null, null)
    assertEquals(expected, file.text)
  }
}