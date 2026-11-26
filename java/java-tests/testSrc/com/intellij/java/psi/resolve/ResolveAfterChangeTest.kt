// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve

import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.impl.source.resolve.ChangedClassView
import com.intellij.psi.impl.source.resolve.ResolveAfterChangeUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class ResolveAfterChangeTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  private val project get() = myFixture.project

  @Test
  fun testRenamedClassIsSeenThroughItsImportOnDemand(): Unit = timeoutRunBlocking {
    myFixture.addClass("package p; public class Old {}")
    val usage = myFixture.addFileToProject("q/Use.java", "package q; import p.*; class Use { Old o; }") as PsiJavaFile

    readAction {
      val target = ResolveAfterChangeUtil.resolveClass(ChangedClassView(findClass("p.Old"), "New", listOf(), null), referenceTo("Old", usage))

      assertEquals(ResolveAfterChangeUtil.Target.Kind.CHANGED, target.kind())
    }
  }

  @Test
  fun testMemberClassOfTheNewNameIsFoundFirst(): Unit = timeoutRunBlocking {
    myFixture.addClass("package p; public class Old {}")
    val usage = myFixture.addFileToProject("q/Use.java", "package q; import p.*; class Use { Old o; class New {} }") as PsiJavaFile

    readAction {
      val target = ResolveAfterChangeUtil.resolveClass(ChangedClassView(findClass("p.Old"), "New", listOf(), null), referenceTo("Old", usage))

      assertEquals(ResolveAfterChangeUtil.Target.Kind.OTHER, target.kind())
      assertEquals(findClass("q.Use.New"), target.element())
    }
  }

  @Test
  fun testTwoClassesOfImportsOnDemandAreAmbiguous(): Unit = timeoutRunBlocking {
    myFixture.addClass("package p; public class Old {}")
    myFixture.addClass("package r; public class New {}")
    val usage = myFixture.addFileToProject("q/Use.java", "package q; import p.*; import r.*; class Use { Old o; }") as PsiJavaFile

    readAction {
      val target = ResolveAfterChangeUtil.resolveClass(ChangedClassView(findClass("p.Old"), "New", listOf(), null), referenceTo("Old", usage))

      assertEquals(ResolveAfterChangeUtil.Target.Kind.AMBIGUOUS, target.kind())
      assertThat(target.elements()).containsExactlyInAnyOrder(findClass("p.Old"), findClass("r.New"))
    }
  }

  @Test
  fun testSingleImportOfAnotherClassWinsUnlessTheChangeDeletesIt(): Unit = timeoutRunBlocking {
    myFixture.addClass("package p; public class Old {}")
    myFixture.addClass("package r; public class New {}")
    val usage = myFixture.addFileToProject("q/Use.java", "package q; import r.New; import p.*; class Use { Old o; }") as PsiJavaFile
    readAction {
      val singleImport = usage.importList!!.findSingleClassImportStatement("r.New")!!

      val kept = ResolveAfterChangeUtil.resolveClass(ChangedClassView(findClass("p.Old"), "New", listOf(), null), referenceTo("Old", usage))
      val deleted = ResolveAfterChangeUtil.resolveClass(ChangedClassView(findClass("p.Old"), "New", listOf(singleImport), null),
                                                        referenceTo("Old", usage))

      assertEquals(ResolveAfterChangeUtil.Target.Kind.OTHER, kept.kind())
      assertEquals(singleImport, kept.fileContext())
      assertEquals(ResolveAfterChangeUtil.Target.Kind.CHANGED, deleted.kind())
    }
  }

  @Test
  fun testClassNotSeenByItsShortNameIsNothing(): Unit = timeoutRunBlocking {
    myFixture.addClass("package p; public class Old {}")
    val usage = myFixture.addFileToProject("q/Use.java", "package q; class Use { p.Old o; }") as PsiJavaFile
    readAction {
      // The use is qualified, and its file has no import of p. So the short name New resolves to no class there, not even the renamed one.
      val target = ResolveAfterChangeUtil.resolveClass(ChangedClassView(findClass("p.Old"), "New", listOf(), null), referenceTo("Old", usage))

      assertEquals(ResolveAfterChangeUtil.Target.Kind.NONE, target.kind())
    }
  }

  @Test
  fun testLocalVariableOfTheNewNameHidesTheField(): Unit = timeoutRunBlocking {
    val file = myFixture.addFileToProject("A.java", "class A { int count; void f() { int total = 0; count++; } }") as PsiJavaFile
    readAction {
      val field = findClass("A").findFieldByName("count", false)!!

      val target = ResolveAfterChangeUtil.resolveField(field, "total", expressionOf("count", file))

      assertEquals(ResolveAfterChangeUtil.Target.Kind.OTHER, target.kind())
    }
  }

  @Test
  fun testFieldIsFoundUnderItsNewName(): Unit = timeoutRunBlocking {
    val file = myFixture.addFileToProject("A.java", "class A { int count; void f() { count++; } }") as PsiJavaFile
    readAction {
      val field = findClass("A").findFieldByName("count", false)!!

      val target = ResolveAfterChangeUtil.resolveField(field, "total", expressionOf("count", file))

      assertEquals(ResolveAfterChangeUtil.Target.Kind.CHANGED, target.kind())
    }
  }

  @Test
  fun testInheritorOfTheRenamedMethodIsTheClassOfTheCall(): Unit = timeoutRunBlocking {
    myFixture.addClass("class Base { void run() {} }")
    val file = myFixture.addFileToProject("Outer.java",
                                          "class Outer { void execute() {} class Inner extends Base { void f() { run(); } } }") as PsiJavaFile
    readAction {
      val run = findClass("Base").findMethodsByName("run", false).single()
      val call = PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java).single()

      assertEquals(findClass("Outer.Inner"), ResolveAfterChangeUtil.methodClass(listOf(run), "execute", call.methodExpression))
    }
  }

  @Test
  fun testMovedClassLeavesItsOldPackage(): Unit = timeoutRunBlocking {
    val fooFile = addFoo()
    val user = myFixture.addFileToProject("a/User.java", "package a; class User { Foo f; }") as PsiJavaFile

    readAction {
      assertEquals(ResolveAfterChangeUtil.Target.Kind.NONE, resolveMoved(listOf(fooFile), user).kind())
    }
  }

  @Test
  fun testMovedClassLeavesTheImportOnDemandOfItsOldPackage(): Unit = timeoutRunBlocking {
    val fooFile = addFoo()
    val use = myFixture.addFileToProject("q/Use.java", "package q; import a.*; class Use { Foo f; }") as PsiJavaFile

    readAction {
      assertEquals(ResolveAfterChangeUtil.Target.Kind.NONE, resolveMoved(listOf(fooFile), use).kind())
    }
  }

  @Test
  fun testMovedClassKeepsItsSingleImport(): Unit = timeoutRunBlocking {
    val fooFile = addFoo()
    val use = myFixture.addFileToProject("q/Use.java", "package q; import a.Foo; class Use { Foo f; }") as PsiJavaFile

    readAction {
      assertEquals(ResolveAfterChangeUtil.Target.Kind.CHANGED, resolveMoved(listOf(fooFile), use).kind())
    }
  }

  @Test
  fun testMovedClassEntersTheTargetPackage(): Unit = timeoutRunBlocking {
    val fooFile = addFoo()
    val bar = myFixture.addFileToProject("c/Bar.java", "package c; class Bar { a.Foo f; }") as PsiJavaFile

    readAction {
      assertEquals(ResolveAfterChangeUtil.Target.Kind.CHANGED, resolveMoved(listOf(fooFile), bar).kind())
    }
  }

  @Test
  fun testMovedClassComesThroughTheImportOnDemandOfTheTargetPackage(): Unit = timeoutRunBlocking {
    val fooFile = addFoo()
    myFixture.addClass("package c; public class Other {}")
    val use = myFixture.addFileToProject("q/Use.java", "package q; import c.*; class Use { a.Foo f; }") as PsiJavaFile

    readAction {
      assertEquals(ResolveAfterChangeUtil.Target.Kind.CHANGED, resolveMoved(listOf(fooFile), use).kind())
    }
  }

  @Test
  fun testMemberClassWinsOverTheMovedClass(): Unit = timeoutRunBlocking {
    val fooFile = addFoo()
    val bar = myFixture.addFileToProject("c/Bar.java", "package c; class Bar { a.Foo f; class Foo {} }") as PsiJavaFile

    readAction {
      val target = resolveMoved(listOf(fooFile), bar)

      assertEquals(ResolveAfterChangeUtil.Target.Kind.OTHER, target.kind())
      assertEquals(findClass("c.Bar.Foo"), target.element())
    }
  }

  @Test
  fun testSingleImportWinsOverTheMovedClassOfTheTargetPackage(): Unit = timeoutRunBlocking {
    val fooFile = addFoo()
    myFixture.addClass("package x; public class Foo {}")
    val bar = myFixture.addFileToProject("c/Bar.java", "package c; import x.Foo; class Bar { a.Foo f; }") as PsiJavaFile

    readAction {
      val target = resolveMoved(listOf(fooFile), bar)

      assertEquals(ResolveAfterChangeUtil.Target.Kind.OTHER, target.kind())
      assertEquals(findClass("x.Foo"), target.element())
    }
  }

  @Test
  fun testPackageClassWinsOverTheMovedClassOfAnImportOnDemand(): Unit = timeoutRunBlocking {
    val fooFile = addFoo()
    myFixture.addClass("package c; public class Other {}")
    myFixture.addClass("package q; class Foo {}")
    val use = myFixture.addFileToProject("q/Use.java", "package q; import c.*; class Use { a.Foo f; }") as PsiJavaFile

    readAction {
      val target = resolveMoved(listOf(fooFile), use)

      assertEquals(ResolveAfterChangeUtil.Target.Kind.OTHER, target.kind())
      assertEquals(findClass("q.Foo"), target.element())
    }
  }

  @Test
  fun testMovedClassAndAClassOfAnotherImportOnDemandAreAmbiguous(): Unit = timeoutRunBlocking {
    val fooFile = addFoo()
    myFixture.addClass("package c; public class Other {}")
    myFixture.addClass("package x; public class Foo {}")
    val use = myFixture.addFileToProject("q/Use.java", "package q; import c.*; import x.*; class Use { a.Foo f; }") as PsiJavaFile

    readAction {
      val target = resolveMoved(listOf(fooFile), use)

      assertEquals(ResolveAfterChangeUtil.Target.Kind.AMBIGUOUS, target.kind())
      assertThat(target.elements()).containsExactlyInAnyOrder(findClass("a.Foo"), findClass("x.Foo"))
    }
  }

  @Test
  fun testMovedFileSeesTheMovedClassOfTheTargetPackage(): Unit = timeoutRunBlocking {
    val fooFile = addFoo()
    val user = myFixture.addFileToProject("a/User.java", "package a; class User { Foo f; }") as PsiJavaFile

    readAction {
      assertEquals(ResolveAfterChangeUtil.Target.Kind.CHANGED, resolveMoved(listOf(fooFile, user), user).kind())
    }
  }

  @Test
  fun testCopyOfTheMovedFileSeesTheClassAsItsOwnClass(): Unit = timeoutRunBlocking {
    val fooFile = myFixture.addFileToProject("a/Foo.java", "package a; public class Foo { Foo next; }") as PsiJavaFile
    val copy = readAction { fooFile.copy() as PsiJavaFile }
    writeCommandAction(project, "Set the package of the copy") { copy.packageName = "c" }

    readAction {
      assertEquals(ResolveAfterChangeUtil.Target.Kind.CHANGED, resolveMoved(listOf(fooFile), copy).kind())
    }
  }

  private fun addFoo(): PsiJavaFile = myFixture.addFileToProject("a/Foo.java", "package a; public class Foo {}") as PsiJavaFile

  private fun resolveMoved(movedFiles: List<PsiJavaFile>, file: PsiJavaFile): ResolveAfterChangeUtil.Target {
    val view = ChangedClassView.moved(findClass("a.Foo"), "c", movedFiles)
    val place = PsiTreeUtil.findChildrenOfType(file, PsiTypeElement::class.java)
      .mapNotNull { it.innermostComponentReferenceElement }
      .first { it.referenceName == "Foo" }
    return ResolveAfterChangeUtil.resolveClass(view, place)
  }

  private fun referenceTo(name: String, file: PsiJavaFile): PsiJavaCodeReferenceElement =
    PsiTreeUtil.findChildrenOfType(file, PsiJavaCodeReferenceElement::class.java).first { it.referenceName == name }

  private fun expressionOf(name: String, file: PsiJavaFile): PsiReferenceExpression =
    PsiTreeUtil.findChildrenOfType(file, PsiReferenceExpression::class.java).first { it.referenceName == name }

  private fun findClass(name: String): PsiClass = JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.projectScope(project))!!
}
