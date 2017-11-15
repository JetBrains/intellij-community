// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.codeInsight.daemon.impl.quickfix.AddExportsDirectiveFix
import com.intellij.codeInsight.daemon.impl.quickfix.AddRequiresDirectiveFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaModule

class AddModuleDirectiveTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  fun testNewRequires() = doRequiresTest(
    "module M { }",
    "module M {\n" +
    "    requires M2;\n" +
    "}")

  fun testRequiresAfterOther() = doRequiresTest(
    "module M {\n" +
    "    requires other;\n" +
    "}",
    "module M {\n" +
    "    requires other;\n" +
    "    requires M2;\n" +
    "}")

  fun testNoDuplicateRequires() = doRequiresTest(
    "module M { requires M2; }",
    "module M { requires M2; }")

  fun testRequiresInIncompleteModule() = doRequiresTest(
    "module M {",
    "module M {\n" +
    "    requires M2;")

  fun testNewExports() = doExportsTest(
    "module M { }",
    "module M {\n" +
    "    exports pkg.m;\n" +
    "}")

  fun testExportsAfterOther() = doExportsTest(
    "module M {\n" +
    "    exports pkg.other;\n" +
    "}",
    "module M {\n" +
    "    exports pkg.other;\n" +
    "    exports pkg.m;\n" +
    "}")

  fun testNoNarrowingExports() = doExportsTest(
    "module M { exports pkg.m; }",
    "module M { exports pkg.m; }")

  fun testNoDuplicateExports() = doExportsTest(
    "module M { exports pkg.m to M1, M2; }",
    "module M { exports pkg.m to M1, M2; }")

  fun testExportsExtendsOther() = doExportsTest(
    "module M {\n" +
    "    exports pkg.m to M1;\n" +
    "}",
    "module M {\n" +
    "    exports pkg.m to M1, M2;\n" +
    "}")

  fun testExportsExtendsIncompleteOther() = doExportsTest(
    "module M {\n" +
    "    exports pkg.m to M1\n" +
    "}",
    "module M {\n" +
    "    exports pkg.m to M1, M2\n" +
    "}")

  private fun doRequiresTest(text: String, expected: String) = doTest(text, { AddRequiresDirectiveFix(it, "M2") }, expected)
  private fun doExportsTest(text: String, expected: String) = doTest(text, { AddExportsDirectiveFix(it, "pkg.m", "M2") }, expected)

  private fun doTest(text: String, fix: (PsiJavaModule) -> IntentionAction, expected: String) {
    val file = myFixture.configureByText("module-info.java", text) as PsiJavaFile
    val action = fix(file.moduleDeclaration!!)
    WriteCommandAction.writeCommandAction(file).run<RuntimeException> { action.invoke(project, editor, file) }
    assertEquals(expected, file.text)
  }
}