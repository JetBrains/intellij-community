// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.lang.documentation.impl.documentationTargets
import com.intellij.lang.java.JavaDocumentationTarget
import com.intellij.openapi.application.readAction
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiTypeParameter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

@TestApplication
class JavaDocumentationTargetProviderTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  private suspend fun collectTargets(): List<DocumentationTarget> {
    return readAction {
      val file = myFixture.file
      val offset = myFixture.editor.caretModel.offset
      documentationTargets(file, offset)
    }
  }

  private suspend fun singleJavaTarget(code: String): JavaDocumentationTarget? {
    val targets = collectTargets()
    assertEquals(1, targets.size, "`$code` has no target")
    return targets.single() as? JavaDocumentationTarget
  }

  private suspend inline fun <reified T : PsiElement> assertTarget(vararg codeInClass: String, crossinline targetConsumer: (String, JavaDocumentationTarget, T) -> Unit) {
    for (code in codeInClass) {
      myFixture.configureByText("A.java", code)
      val target = singleJavaTarget(code)
      readAction {
        assertNotNull(target, "null target: `$code`")
        val element = target!!.element
        assertIs<T>(element, "targeting $element: `$code`")
        targetConsumer(code, target, element)
      }
    }
  }

  private suspend inline fun <reified T : PsiElement> assertSingleTargetElement(vararg codeInClass: String): T {
    var last: T? = null
    for (code in codeInClass) {
      myFixture.configureByText("A.java", code)
      val element = singleJavaTarget(code)?.element
      last = readAction { assertIs<T>(element, "`$code`: targeting $element.") }
    }
    return last!!
  }

  private suspend inline fun <reified T : PsiElement> assertSingleTargetElementWithPrefix(prefix: String, vararg code: String): T {
    return assertSingleTargetElement<T>(*code.map { "$prefix\n$it" }.toTypedArray())
  }

  private suspend fun assertNoTargetElement(vararg codeInClass: String) {
    for (code in codeInClass) {
      myFixture.configureByText("A.java", code)
      val target = collectTargets().singleOrNull()
      assert(target == null || target !is JavaDocumentationTarget) {
        "`$code` targets ${
          when (target) {
            is JavaDocumentationTarget -> target.element.text
            else -> target
          }
        }, but no target was expected"
      }
    }
  }

  private suspend inline fun <reified T : PsiElement> assertSingleTargetElementInFile(fileName: String, vararg code: String): T {
    var last: T? = null
    for (text in code) {
      myFixture.configureByText(fileName, text)
      val element = singleJavaTarget(text)?.element
      last = readAction { assertIs<T>(element, "`$text`: targeting $element.") }
    }
    return last!!
  }

  @Test
  fun testImportTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val import = assertSingleTargetElementInFile<PsiPackage>(
      "Test.java",
      "import <caret>java.util.*;",
      "import java<caret>.util.*;",
    )
    assertEquals("java", import.qualifiedName)
  }

  @Test
  fun testImportTarget2(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val import = assertSingleTargetElementInFile<PsiPackage>(
      "Test.java",
      "import java.<caret>util.*;",
      "import java.ut<caret>il.*;",
      "import java.util<caret>.*;",
    )
    assertEquals("java.util", import.qualifiedName)
  }

  @Test
  fun testMethodTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val method = assertSingleTargetElement<PsiMethod>(
      "public String <caret>foo(Object o) {}",
      "public String foo<caret>(Object o) {}",
      "public String foo<caret> (Object o) {}",
      "/** <caret> */ public String foo(Object o) {}",
    )
    assertEquals("foo", method.name)
  }

  @Test
  fun testMethodCallTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val method = assertSingleTargetElement<PsiMethod>(
      "void foo(int a) {} void bar() { <caret>foo(); }",
      "void foo(int a) {} void bar() { foo<caret>(); }",
    )

    assertEquals("foo", method.name)
  }

  @Test
  fun testMethodCallExpressionTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val callExpr = assertSingleTargetElement<PsiMethodCallExpression>(
      "void foo(int a) {} void bar() { foo(<caret>); }",
      "void foo(int a) {} void bar() { foo(12<caret>345); }",
      "void foo(int a) {} void bar() { foo(x, y, <caret>); }",
    )

    assertEquals("foo", callExpr.methodExpression.referenceName)
  }

  @Test
  fun testMethodForceCandidates(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertTarget<PsiMethodCallExpression>(
      "void foo() { new StringBuilder().append(<caret>); }",
      "void foo() { new StringBuilder().append(1<caret>); }",
      "void foo() { new StringBuilder().append(tr<caret>ue); }",
    ) { code, target, callExpr ->
      assertTrue(target.showAllCandidates, "candidates not shown: `$code`")
      assertEquals("append", callExpr.methodExpression.referenceName)
    }

    assertTarget<PsiMethodCallExpression>(
      "void foo() { new StringBuilder().app<caret>end(); }",
      "void foo() { new StringBuilder().append<caret>(); }",
    ) { code, target, callExpr ->
      assertFalse(target.showAllCandidates, "candidates shown for $code")
      assertEquals("append", callExpr.methodExpression.referenceName)
    }

    assertTarget<PsiMethod>(
      "void foo() { new StringBuilder().app<caret>end(1); }",
      "void foo() { new StringBuilder().append<caret>(1); }",
    ) { code, target, method ->
      assertFalse(target.showAllCandidates, "candidates shown for $code")
      assertEquals("append", method.name)
    }
  }

  @Test
  fun testNewExpressionCandidates(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertTarget<PsiNewExpression>(
      "void foo() { new StringBuilder(<caret>).append(1); }",
      "void foo() { new StringBuilder(<caret> }",
      "void foo() { new StringBuilder<caret>",
    ) { code, target, newExpr ->
      assertTrue(target.showAllCandidates, "candidates not shown: `$code`")
      assertEquals("java.lang.StringBuilder", newExpr.classReference?.qualifiedName)
    }

    assertTarget<PsiMethod>(
      "void foo() { new String<caret>Builder().append(1); }",
    ) { code, target, newExpr ->
      assertFalse(target.showAllCandidates, "candidates shown: `$code`")
      assertEquals("java.lang.StringBuilder", newExpr.containingClass?.qualifiedName)
    }
  }

  @Test
  fun testAnnotationTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val ann = assertSingleTargetElement<PsiClass>(
      "public @D<caret>eprecated String foo(Object o) {}",
      "public @Deprecated<caret> String foo(Object o) {}",
    )
    assertEquals("java.lang.Deprecated", ann.qualifiedName)
  }

  @Test
  fun testNameValuePairTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val method = assertSingleTargetElementWithPrefix<PsiAnnotationMethod>(
      """
      @Target(value={METHOD})
      public @interface Foo { booolean forRemoval() default false; }
      """,
      "public @Foo(<caret>forRemoval = true) String foo(Object o) {}",
    )
    assertEquals("forRemoval", method.name)
  }

  @Test
  fun testReturnTypeTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val cls = assertSingleTargetElement<PsiClass>(
      "public <caret>String foo(Object o) {}",
      "public Str<caret>ing foo(Object o) {}",
      "public String<caret> foo(Object o) {}",
    )
    assertEquals("java.lang.String", cls.qualifiedName)
  }

  @Test
  fun testParameterTypeTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val cls = assertSingleTargetElement<PsiClass>(
      "public String foo(<caret>Object o) {}",
      "public String foo(Obj<caret>ect o) {}",
      "public String foo(Object<caret> o) {}",
    )
    assertEquals("java.lang.Object", cls.qualifiedName)
  }

  @Test
  fun testParameterNameTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val parameter = assertSingleTargetElement<PsiParameter>(
      "public String foo(Object <caret>bar) {}",
      "public String foo(Object b<caret>ar) {}",
      "public String foo(Object bar<caret>) {}",
      "public String foo(Object bar<caret> ) {}",
    )
    assertEquals("bar", parameter.name)
  }

  @Test
  fun testJavadocParameterTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val parameter = assertSingleTargetElement<PsiParameter>(
      "/** @param <caret>a description */ void foo(String a) {}",
    )
    assertEquals("a", parameter.name)
  }

  @Test
  fun testClassTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val cls = assertSingleTargetElementInFile<PsiClass>(
      "A.java",
      "class <caret>A {}",
      "class A<caret> {}",
      "/** test <caret> test */ class A {}",
    )
    assertEquals("A", cls.name)
  }

  @Test
  fun testNewExpressionTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertSingleTargetElement<PsiNewExpression>(
      "class A { StringBuilder s = new StringBuilder(<caret>); }",
      "class A { StringBuilder s = new StringBuilder(12<caret>3); }",
    )
  }

  @Test
  fun testFieldTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val field = assertSingleTargetElement<PsiField>(
      "int <caret>xx;",
      "int x<caret>x;",
      "int xx<caret>;",
    )
    readAction { assertEquals("xx", field.name) }
  }

  @Test
  fun testLocalVariableTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val variable = assertSingleTargetElement<PsiLocalVariable>(
      "void foo() { var <caret>x = 1; }",
      "void foo() { var x = 1; <caret>x.hashCode(); }",
    )
    assertEquals("x", variable.name)
  }

  @Test
  fun testTypeParameterTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val tp = assertSingleTargetElementInFile<PsiTypeParameter>(
      "A.java",
      "class A<<caret>T> {}",
    )
    readAction { assertEquals("T", tp.name) }
  }

  @Test
  fun testAnnotationMethodTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val m = assertSingleTargetElementInFile<PsiAnnotationMethod>(
      "Ann.java",
      "@interface Ann { int <caret>value(); }",
      "@interface Ann { int value<caret>(); }",
    )
    assertEquals("value", m.name)
  }

  @Test
  fun testEnumConstantTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val constant = assertSingleTargetElementInFile<PsiEnumConstant>(
      "E.java",
      "enum E { <caret>RED; }",
    )
    readAction { assertEquals("RED", constant.name) }
  }

  @Test
  fun testEnumConstantInitializerTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val init = assertSingleTargetElementInFile<PsiEnumConstant>(
      "E.java",
      "enum E { <caret>X{}; }",
      "enum E { X<caret>{}; }",
    )
    readAction { assertEquals("X", init.name) }
  }

  @Test
  fun testAnonymousClassTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val anon = assertSingleTargetElement<PsiClass>(
      "Runnable r = new Runn<caret>able() { public void run(){} };",
      "Runnable r = new Runnable<caret>() { public void run(){} };",
    )
    assertEquals("java.lang.Runnable", anon.qualifiedName)
  }

  @Test
  fun testModuleTarget(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val module = assertSingleTargetElementInFile<PsiJavaModule>(
      "module-info.java",
      "module <caret>m { }",
      "module m<caret> { }",
      "/** test <caret> test */ module m { }",
    )
    assertEquals("m", module.name)
  }

  @Test
  fun testClassAccessExpression(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val clazz = assertSingleTargetElement<PsiClass>(
      "class A { public A(String s) {} } void main() { new A(<caret>String.class.getName()); }",
      "class A { public A(String s) {} } void main() { new A(String<caret>.class.getName()); }",
    )
    assertEquals("java.lang.String", clazz.qualifiedName)
  }

  @Test
  fun testPackage(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val pack = assertSingleTargetElement<PsiPackage>(
      "package java.ut<caret>il;",
    )
    assertEquals("java.util", pack.qualifiedName)
  }

  @Test
  fun testPackageInfo(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertSingleTargetElementInFile<PsiPackage>(
      "package-info.java",
      "/** test <caret> test */ package foo.bar;", // IJPL-32515
    )
  }

  @Test
  fun testSubPackage(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val pack = assertSingleTargetElement<PsiPackage>(
      "package ja<caret>va.util;",
    )
    assertEquals("java", pack.qualifiedName)
  }

  @Test
  fun testNoTargets(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertNoTargetElement(
      "class A { <caret> }",
      "void foo() <caret>{}",
      "void foo() { <caret> }",
    )
  }
}
