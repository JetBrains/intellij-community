// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.impl.source.resolve.RenamedClassView
import com.intellij.psi.impl.source.resolve.ResolveAfterRenameUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ResolveAfterRenameTest : LightJavaCodeInsightFixtureTestCase() {
  fun testRenamedClassIsSeenThroughItsImportOnDemand() {
    myFixture.addClass("package p; public class Old {}")
    val usage = myFixture.addFileToProject("q/Use.java", "package q; import p.*; class Use { Old o; }") as PsiJavaFile

    val target = ResolveAfterRenameUtil.resolveClass(RenamedClassView(findClass("p.Old"), "New", listOf(), null), referenceTo("Old", usage))

    assertEquals(ResolveAfterRenameUtil.Target.Kind.RENAMED, target.kind())
  }

  fun testMemberClassOfTheNewNameIsFoundFirst() {
    myFixture.addClass("package p; public class Old {}")
    val usage = myFixture.addFileToProject("q/Use.java", "package q; import p.*; class Use { Old o; class New {} }") as PsiJavaFile

    val target = ResolveAfterRenameUtil.resolveClass(RenamedClassView(findClass("p.Old"), "New", listOf(), null), referenceTo("Old", usage))

    assertEquals(ResolveAfterRenameUtil.Target.Kind.OTHER, target.kind())
    assertEquals(findClass("q.Use.New"), target.element())
  }

  fun testTwoClassesOfImportsOnDemandAreAmbiguous() {
    myFixture.addClass("package p; public class Old {}")
    myFixture.addClass("package r; public class New {}")
    val usage = myFixture.addFileToProject("q/Use.java", "package q; import p.*; import r.*; class Use { Old o; }") as PsiJavaFile

    val target = ResolveAfterRenameUtil.resolveClass(RenamedClassView(findClass("p.Old"), "New", listOf(), null), referenceTo("Old", usage))

    assertEquals(ResolveAfterRenameUtil.Target.Kind.AMBIGUOUS, target.kind())
    assertSameElements(target.elements(), findClass("p.Old"), findClass("r.New"))
  }

  fun testSingleImportOfAnotherClassWinsUnlessTheChangeDeletesIt() {
    myFixture.addClass("package p; public class Old {}")
    myFixture.addClass("package r; public class New {}")
    val usage = myFixture.addFileToProject("q/Use.java", "package q; import r.New; import p.*; class Use { Old o; }") as PsiJavaFile
    val singleImport = usage.importList!!.findSingleClassImportStatement("r.New")!!

    val kept = ResolveAfterRenameUtil.resolveClass(RenamedClassView(findClass("p.Old"), "New", listOf(), null), referenceTo("Old", usage))
    val deleted = ResolveAfterRenameUtil.resolveClass(RenamedClassView(findClass("p.Old"), "New", listOf(singleImport), null),
                                                      referenceTo("Old", usage))

    assertEquals(ResolveAfterRenameUtil.Target.Kind.OTHER, kept.kind())
    assertEquals(singleImport, kept.fileContext())
    assertEquals(ResolveAfterRenameUtil.Target.Kind.RENAMED, deleted.kind())
  }

  fun testClassNotSeenByItsShortNameIsNothing() {
    myFixture.addClass("package p; public class Old {}")
    val usage = myFixture.addFileToProject("q/Use.java", "package q; class Use { p.Old o; }") as PsiJavaFile
    // The use is qualified, and its file has no import of p. So the short name New resolves to no class there, not even the renamed one.
    val target = ResolveAfterRenameUtil.resolveClass(RenamedClassView(findClass("p.Old"), "New", listOf(), null), referenceTo("Old", usage))

    assertEquals(ResolveAfterRenameUtil.Target.Kind.NONE, target.kind())
  }

  fun testLocalVariableOfTheNewNameHidesTheField() {
    val file = myFixture.addFileToProject("A.java", "class A { int count; void f() { int total = 0; count++; } }") as PsiJavaFile
    val field = findClass("A").findFieldByName("count", false)!!

    val target = ResolveAfterRenameUtil.resolveField(field, "total", expressionOf("count", file))

    assertEquals(ResolveAfterRenameUtil.Target.Kind.OTHER, target.kind())
  }

  fun testFieldIsFoundUnderItsNewName() {
    val file = myFixture.addFileToProject("A.java", "class A { int count; void f() { count++; } }") as PsiJavaFile
    val field = findClass("A").findFieldByName("count", false)!!

    val target = ResolveAfterRenameUtil.resolveField(field, "total", expressionOf("count", file))

    assertEquals(ResolveAfterRenameUtil.Target.Kind.RENAMED, target.kind())
  }

  fun testInheritorOfTheRenamedMethodIsTheClassOfTheCall() {
    myFixture.addClass("class Base { void run() {} }")
    val file = myFixture.addFileToProject("Outer.java",
                                          "class Outer { void execute() {} class Inner extends Base { void f() { run(); } } }") as PsiJavaFile
    val run = findClass("Base").findMethodsByName("run", false).single()
    val call = PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java).single()

    assertEquals(findClass("Outer.Inner"), ResolveAfterRenameUtil.methodClass(listOf(run), "execute", call.methodExpression))
  }

  private fun referenceTo(name: String, file: PsiJavaFile): PsiJavaCodeReferenceElement =
    PsiTreeUtil.findChildrenOfType(file, PsiJavaCodeReferenceElement::class.java).first { it.referenceName == name }

  private fun expressionOf(name: String, file: PsiJavaFile): PsiReferenceExpression =
    PsiTreeUtil.findChildrenOfType(file, PsiReferenceExpression::class.java).first { it.referenceName == name }

  private fun findClass(name: String): PsiClass = JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.projectScope(project))!!
}
