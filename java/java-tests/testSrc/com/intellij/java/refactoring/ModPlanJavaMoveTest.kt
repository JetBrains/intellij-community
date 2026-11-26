// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanExecutionResult
import com.intellij.refactoring.modplan.ModPlanExecutor
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.move.ModPlanMoveOptions
import com.intellij.refactoring.modplan.move.ModPlanMoveProblem
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.ModPlanMoveTestSupport
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ModPlanJavaMoveTest : LightJavaCodeInsightFixtureTestCase() {
  private fun root(): VirtualFile = myFixture.tempDirFixture.getFile("/")!!

  private fun urlOf(path: String): String = if (path.isEmpty()) root().url else root().url + "/" + path

  private fun fileOf(path: String): PsiElement = PsiManager.getInstance(project).findFile(root().findFileByRelativePath(path)!!)!!

  private fun plan(subjects: List<PsiElement>, targetPath: String, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY): ModPlan =
    ModPlanMoveTestSupport.planOnModPlan(project, subjects, urlOf(targetPath), optionValues)

  private fun accepted(
    subjects: List<PsiElement>,
    targetPath: String,
    optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY,
  ): ModPlan.AcceptedPlan = when (val plan = plan(subjects, targetPath, optionValues)) {
    is ModPlan.RefusedPlan -> throw AssertionError("refused: ${plan.reason} ${plan.message.orEmpty()}")
    is ModPlan.AcceptedPlan -> plan
  }

  private fun execute(plan: ModPlan.AcceptedPlan) {
    assertEquals(ModPlanExecutionResult.Applied, ModPlanExecutor.execute(plan.execution, true, plan.writeEvents))
  }

  private fun move(subjects: List<PsiElement>, targetPath: String, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY) {
    execute(accepted(subjects, targetPath, optionValues))
  }

  private fun moveFoo(targetPath: String = "c", optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY) {
    move(listOf(fileOf("a/Foo.java")), targetPath, optionValues)
  }

  private fun textOf(path: String): String = FileDocumentManager.getInstance().getDocument(root().findFileByRelativePath(path)!!)!!.text

  private fun addFooAndOther() {
    myFixture.addFileToProject("a/Foo.java", "package a; public class Foo { public static void bar() {} }")
    myFixture.addFileToProject("c/Other.java", "package c; public class Other {}")
  }

  fun `test a moved file gets the target package and a single import gets the new name`() {
    addFooAndOther()
    myFixture.addFileToProject("b/User.java", "package b; import a.Foo; class User { Foo f; }")

    moveFoo()

    assertTrue(textOf("c/Foo.java").startsWith("package c;"))
    assertEquals("package b; import c.Foo; class User { Foo f; }", textOf("b/User.java"))
  }

  fun `test a short reference in the old package gets an import`() {
    addFooAndOther()
    myFixture.addFileToProject("a/Old.java", "package a; class Old { Foo f; }")

    moveFoo()

    assertEquals("package a;\n\nimport c.Foo;\n\nclass Old { Foo f; }", textOf("a/Old.java"))
  }

  fun `test the qualifier of a static call gets an import`() {
    addFooAndOther()
    myFixture.addFileToProject("b/Call.java", "package b; class Call { void m() { a.Foo.bar(); } }")

    moveFoo()

    assertEquals("package b;\n\nimport c.Foo;\n\nclass Call { void m() { Foo.bar(); } }", textOf("b/Call.java"))
  }

  fun `test a qualified reference in the target package becomes short`() {
    addFooAndOther()
    myFixture.addFileToProject("c/Bar.java", "package c; class Bar { a.Foo f; }")

    moveFoo()

    assertEquals("package c; class Bar { Foo f; }", textOf("c/Bar.java"))
  }

  fun `test a single import in the target package goes away`() {
    addFooAndOther()
    myFixture.addFileToProject("c/Bar.java", "package c; import a.Foo; class Bar { Foo f; }")

    moveFoo()

    assertEquals("package c;\n\nclass Bar { Foo f; }", textOf("c/Bar.java"))
  }

  fun `test an import on demand of the target package stays`() {
    addFooAndOther()
    myFixture.addFileToProject("q/Use.java", "package q; import c.*; class Use { a.Foo f; }")

    moveFoo()

    assertEquals("package q; import c.*; class Use { Foo f; }", textOf("q/Use.java"))
  }

  fun `test an import on demand of the old package that only the moved class used goes away`() {
    addFooAndOther()
    myFixture.addFileToProject("a/Old.java", "package a; class Old { Foo f; }")
    myFixture.addFileToProject("q/Use.java", "package q; import a.*; class Use { Foo f; }")

    moveFoo()

    assertEquals("package q;\n\nimport c.Foo;\n\nclass Use { Foo f; }", textOf("q/Use.java"))
  }

  fun `test a static import gets the new qualified name`() {
    addFooAndOther()
    myFixture.addFileToProject("q/Use.java", "package q; import static a.Foo.bar; class Use { void m() { bar(); } }")

    moveFoo()

    assertEquals("package q; import static c.Foo.bar; class Use { void m() { bar(); } }", textOf("q/Use.java"))
  }

  fun `test an import of a nested class gets the new qualifier`() {
    myFixture.addFileToProject("a/Foo.java", "package a; public class Foo { public static class Inner {} }")
    myFixture.addFileToProject("c/Other.java", "package c; public class Other {}")
    myFixture.addFileToProject("q/Use.java", "package q; import a.Foo.Inner; class Use { Inner i; }")

    moveFoo()

    assertEquals("package q; import c.Foo.Inner; class Use { Inner i; }", textOf("q/Use.java"))
  }

  fun `test a qualified reference keeps its type arguments and annotations`() {
    myFixture.addFileToProject("a/Foo.java", "package a; public class Foo<T> {}")
    myFixture.addFileToProject("c/Other.java", "package c; public class Other {}")
    myFixture.addFileToProject("q/Ann.java",
                               "package q; import java.lang.annotation.*; @Target(ElementType.TYPE_USE) public @interface Ann {}")
    myFixture.addFileToProject("q/Use.java", "package q; class Use { a.@Ann Foo<String> f; }")

    moveFoo()

    assertEquals("package q;\n\nimport c.Foo;\n\nclass Use { @Ann Foo<String> f; }", textOf("q/Use.java"))
  }

  fun `test a reference in Javadoc gets the form of the legacy move`() {
    addFooAndOther()
    myFixture.addFileToProject("q/Use.java", "package q; /** {@link a.Foo} */ class Use {}")

    moveFoo()

    assertEquals("package q;\n\nimport c.Foo;\n\n/** {@link Foo} */ class Use {}", textOf("q/Use.java"))
  }

  fun `test a class reference in a properties file gets the new name`() {
    addFooAndOther()
    myFixture.addFileToProject("p/my.properties", "ref = a.Foo")

    moveFoo()

    assertEquals("ref = c.Foo", textOf("p/my.properties"))
  }

  fun `test a short reference gets the qualified name when the code style asks for it`() {
    addFooAndOther()
    JavaCodeStyleSettings.getInstance(project).USE_FQ_CLASS_NAMES = true
    myFixture.addFileToProject("a/Old.java", "package a; class Old { Foo f; }")
    myFixture.addFileToProject("q/Use.java", "package q; import a.Foo; class Use { Foo f; a.Foo g; }")

    moveFoo()

    assertEquals("package a; class Old { c.Foo f; }", textOf("a/Old.java"))
    assertEquals("package q; import c.Foo; class Use { Foo f; c.Foo g; }", textOf("q/Use.java"))
  }

  fun `test an annotation of a moved package-info binds to the class that stays`() {
    addFooAndOther()
    myFixture.addFileToProject("a/Ann.java", "package a; public @interface Ann {}")
    myFixture.addFileToProject("a/package-info.java", "@Ann package a;")

    move(listOf(fileOf("a/package-info.java")), "c")

    assertEquals("@Ann\npackage c;\n\nimport a.Ann;", textOf("c/package-info.java"))
  }

  fun `test a moved file keeps its import on demand of the old package`() {
    myFixture.addFileToProject("a/Foo.java", "package a; import a.*; public class Foo { Helper h; }")
    myFixture.addFileToProject("a/Helper.java", "package a; public class Helper {}")
    myFixture.addFileToProject("c/Other.java", "package c; public class Other {}")

    moveFoo()

    assertEquals("package c; import a.*; public class Foo { Helper h; }", textOf("c/Foo.java"))
  }

  fun `test a moved file imports a class of its old package that stays`() {
    myFixture.addFileToProject("a/Foo.java", "package a; public class Foo { Helper h; }")
    myFixture.addFileToProject("a/Helper.java", "package a; public class Helper {}")
    myFixture.addFileToProject("c/Other.java", "package c; public class Other {}")

    moveFoo()

    assertEquals("package c;\n\nimport a.Helper;\n\npublic class Foo { Helper h; }", textOf("c/Foo.java"))
  }

  fun `test a moved file keeps the class of an import on demand that a class of the target package would shadow`() {
    myFixture.addFileToProject("a/Foo.java", "package a;\n\nimport x.*;\n\npublic class Foo { Other o; }")
    myFixture.addFileToProject("x/Other.java", "package x; public class Other {}")
    myFixture.addFileToProject("c/Other.java", "package c; public class Other {}")

    moveFoo()

    assertEquals("package c;\n\nimport x.Other;\n\npublic class Foo { Other o; }", textOf("c/Foo.java"))
  }

  fun `test a moved file keeps the class of java lang that a class of the target package would shadow`() {
    myFixture.addFileToProject("a/Foo.java", "package a; public class Foo { Error e; }")
    myFixture.addFileToProject("c/Error.java", "package c; public class Error extends Exception {}")

    moveFoo()

    assertEquals("package c;\n\nimport java.lang.Error;\n\npublic class Foo { Error e; }", textOf("c/Foo.java"))
  }

  fun `test moved files keep their short references to each other`() {
    addFooAndOther()
    myFixture.addFileToProject("a/Bar.java", "package a; public class Bar { Foo f; }")

    move(listOf(fileOf("a/Foo.java"), fileOf("a/Bar.java")), "c")

    assertEquals("package c; public class Bar { Foo f; }", textOf("c/Bar.java"))
  }

  fun `test package-info gets the package statement after its doc`() {
    addFooAndOther()
    myFixture.addFileToProject("a/package-info.java", "/** Doc. */\npackage a;")

    move(listOf(fileOf("a/package-info.java")), "c")

    assertEquals("/** Doc. */\npackage c;", textOf("c/package-info.java"))
  }

  fun `test a moved file goes into a target directory that does not exist yet`() {
    addFooAndOther()

    moveFoo("c/x")

    assertTrue(textOf("c/x/Foo.java").startsWith("package c.x;"))
  }

  fun `test a move to a new directory is not supported when the module exports the old package`() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_9) {
      myFixture.addFileToProject("module-info.java", "module m { exports a; }")
      addFooAndOther()

      val refusal = plan(listOf(fileOf("a/Foo.java")), "c/x") as ModPlan.RefusedPlan

      assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusal.reason)
    }
  }

  fun `test a move to an existing directory is not supported when the module exports the old package`() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_9) {
      myFixture.addFileToProject("module-info.java", "module m { exports a; }")
      addFooAndOther()

      val refusal = plan(listOf(fileOf("a/Foo.java")), "c") as ModPlan.RefusedPlan

      assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusal.reason)
    }
  }

  fun `test a mixed batch moves a Java file and a text file in one plan`() {
    addFooAndOther()
    myFixture.addFileToProject("d/notes.txt", "text")

    move(listOf(fileOf("a/Foo.java"), fileOf("d/notes.txt")), "c")

    assertTrue(textOf("c/Foo.java").startsWith("package c;"))
    assertEquals("text", textOf("c/notes.txt"))
  }

  fun `test a move without the search for references changes only the moved file`() {
    addFooAndOther()
    myFixture.addFileToProject("b/User.java", "package b; import a.Foo; class User { Foo f; }")

    moveFoo(optionValues = ModPlanOptionValues.EMPTY.with(ModPlanMoveOptions.SEARCH_FOR_REFERENCES, false))

    assertTrue(textOf("c/Foo.java").startsWith("package c;"))
    assertEquals("package b; import a.Foo; class User { Foo f; }", textOf("b/User.java"))
  }

  fun `test an excluded reference keeps its text`() {
    addFooAndOther()
    myFixture.addFileToProject("b/User.java", "package b; class User { a.Foo f; a.Foo g; }")
    val first = accepted(listOf(fileOf("a/Foo.java")), "c")
    val user = root().findFileByRelativePath("b/User.java")!!
    val offset = textOf("b/User.java").lastIndexOf("a.Foo")
    val place = first.usages.single { it.virtualFile == user && it.range?.startOffset in offset until offset + "a.Foo".length }
    val excluded = runReadActionBlocking { ModPlanRefactoring.excludedUsagesOf(listOf(place)) }

    moveFoo(optionValues = ModPlanOptionValues.EMPTY.with(ModPlanRefactoring.EXCLUDED_USAGES, excluded))

    assertEquals("package b;\n\nimport c.Foo;\n\nclass User { Foo f; a.Foo g; }", textOf("b/User.java"))
  }

  fun `test an unresolved reference of a moved file keeps its text`() {
    myFixture.addFileToProject("a/Foo.java", "package a; public class Foo { Missing m; }")
    myFixture.addFileToProject("c/Other.java", "package c; public class Other {}")

    moveFoo()

    assertEquals("package c; public class Foo { Missing m; }", textOf("c/Foo.java"))
  }

  fun `test a package-local class used from another package is a conflict`() {
    myFixture.addFileToProject("a/Foo.java", "package a; class Foo {}")
    myFixture.addFileToProject("a/User.java", "package a; class User { Foo f; }")
    myFixture.addFileToProject("c/Other.java", "package c; public class Other {}")

    val ready = accepted(listOf(fileOf("a/Foo.java")), "c")

    val messages = ready.conflicts.conflicts.flatMap { it.messages }
    assertEquals(listOf("Package-local class <b><code>Foo</code></b> will no longer be accessible from field <b><code>User.f</code></b>"),
                 messages)
  }

  fun `test a target with no package is refused`() {
    addFooAndOther()
    myFixture.addFileToProject("b/my-dir/readme.txt", "text")

    val refusal = plan(listOf(fileOf("a/Foo.java")), "b/my-dir") as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refusal.reason)
    assertEquals(ModPlanMoveProblem.INVALID_DESTINATION.message(), refusal.message)
  }

  fun `test a file in its target directory is refused`() {
    addFooAndOther()

    val refusal = plan(listOf(fileOf("a/Foo.java")), "a") as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refusal.reason)
    assertEquals(ModPlanMoveProblem.ALREADY_IN_DESTINATION.message(), refusal.message)
  }

  fun `test a package is not supported`() {
    addFooAndOther()

    val refusal = plan(listOf(JavaPsiFacade.getInstance(project).findPackage("a")!!), "c") as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusal.reason)
  }

  fun `test a file with several classes moves with all its classes`() {
    myFixture.addFileToProject("a/A.java", "package a; public class A { B b; }\nclass B {}")
    myFixture.addFileToProject("c/Other.java", "package c; public class Other {}")

    move(listOf(fileOf("a/A.java")), "c")

    assertEquals("package c; public class A { B b; }\nclass B {}", textOf("c/A.java"))
    assertNull(root().findFileByRelativePath("a/A.java"))
  }

  fun `test a class is not supported`() {
    addFooAndOther()

    val refusal = plan(listOf(myFixture.findClass("a.Foo")), "c") as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusal.reason)
  }
}