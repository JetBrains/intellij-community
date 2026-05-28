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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import kotlin.test.assertIs

class JavaDocumentationTargetProviderTest : LightJavaCodeInsightFixtureTestCase() {

  private fun collectTargets(): List<DocumentationTarget> {
    val file = myFixture.file
    val offset = myFixture.editor.caretModel.offset
    return timeoutRunBlocking {
      readAction {
        documentationTargets(file, offset)
      }
    }
  }

  private fun singleJavaTarget(code: String): JavaDocumentationTarget? {
    val targets = collectTargets()
    assertEquals("`$code` has no target", 1, targets.size)
    return targets.single() as? JavaDocumentationTarget
  }

  private inline fun <reified T : PsiElement> assertTarget(vararg codeInClass: String, targetConsumer: (String, JavaDocumentationTarget, T) -> Unit) {
    for (code in codeInClass) {
      myFixture.configureByText("A.java", code)
      val target = singleJavaTarget(code)
      assertNotNull("null target: `$code`", target)
      val element = target!!.element
      assertIs<T>(element, "targeting $element: `$code`")
      targetConsumer(code, target, element)
    }
  }

  private inline fun <reified T : PsiElement> assertSingleTargetElement(vararg codeInClass: String): T {
    var last: T? = null
    for (code in codeInClass) {
      myFixture.configureByText("A.java", code)
      val element = singleJavaTarget(code)?.element
      last = assertIs<T>(element, "`$code`: targeting $element.")
    }
    return last!!
  }

  private inline fun <reified T : PsiElement> assertSingleTargetElementWithPrefix(prefix: String, vararg code: String): T {
    return assertSingleTargetElement<T>(*code.map { "$prefix\n$it" }.toTypedArray())
  }

  private fun assertNoTargetElement(vararg codeInClass: String) {
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

  private inline fun <reified T : PsiElement> assertSingleTargetElementInFile(fileName: String, vararg code: String): T {
    var last: T? = null
    for (text in code) {
      myFixture.configureByText(fileName, text)
      val element = singleJavaTarget(text)?.element
      last = assertIs<T>(element, "`$text`: targeting $element.")
    }
    return last!!
  }

  fun testImportTarget() {
    val import = assertSingleTargetElementInFile<PsiPackage>(
      "Test.java",
      "import <caret>java.util.*;",
      "import java<caret>.util.*;",
    )
    assertEquals("java", import.qualifiedName)
  }

  fun testImportTarget2() {
    val import = assertSingleTargetElementInFile<PsiPackage>(
      "Test.java",
      "import java.<caret>util.*;",
      "import java.ut<caret>il.*;",
      "import java.util<caret>.*;",
    )
    assertEquals("java.util", import.qualifiedName)
  }

  fun testMethodTarget() {
    val method = assertSingleTargetElement<PsiMethod>(
      "public String <caret>foo(Object o) {}",
      "public String foo<caret>(Object o) {}",
      "public String foo<caret> (Object o) {}",
      "/** <caret> */ public String foo(Object o) {}",
    )
    assertEquals("foo", method.name)
  }

  fun testMethodCallTarget() {
    val method = assertSingleTargetElement<PsiMethod>(
      "void foo(int a) {} void bar() { <caret>foo(); }",
      "void foo(int a) {} void bar() { foo<caret>(); }",
    )

    assertEquals("foo", method.name)
  }

  fun testMethodCallExpressionTarget() {
    val callExpr = assertSingleTargetElement<PsiMethodCallExpression>(
      "void foo(int a) {} void bar() { foo(<caret>); }",
      "void foo(int a) {} void bar() { foo(12<caret>345); }",
      "void foo(int a) {} void bar() { foo(x, y, <caret>); }",
    )

    assertEquals("foo", callExpr.methodExpression.referenceName)
  }

  fun testMethodForceCandidates() {
    assertTarget<PsiMethodCallExpression>(
      "void foo() { new StringBuilder().append(<caret>); }",
      "void foo() { new StringBuilder().append(1<caret>); }",
      "void foo() { new StringBuilder().append(tr<caret>ue); }",
    ) { code, target, callExpr ->
      assertTrue("candidates not shown: `$code`", target.showAllCandidates)
      assertEquals("append", callExpr.methodExpression.referenceName)
    }

    assertTarget<PsiMethodCallExpression>(
      "void foo() { new StringBuilder().app<caret>end(); }",
      "void foo() { new StringBuilder().append<caret>(); }",
    ) { code, target, callExpr ->
      assertFalse("candidates shown for $code", target.showAllCandidates)
      assertEquals("append", callExpr.methodExpression.referenceName)
    }

    assertTarget<PsiMethod>(
      "void foo() { new StringBuilder().app<caret>end(1); }",
      "void foo() { new StringBuilder().append<caret>(1); }",
    ) { code, target, method ->
      assertFalse("candidates shown for $code", target.showAllCandidates)
      assertEquals("append", method.name)
    }
  }

  fun testNewExpressionCandidates() {
    assertTarget<PsiNewExpression>(
      "void foo() { new StringBuilder(<caret>).append(1); }",
      "void foo() { new StringBuilder(<caret> }",
      "void foo() { new StringBuilder<caret>",
    ) { code, target, newExpr ->
      assertTrue("candidates not shown: `$code`", target.showAllCandidates)
      assertEquals("java.lang.StringBuilder", newExpr.classReference?.qualifiedName)
    }

    assertTarget<PsiMethod>(
      "void foo() { new String<caret>Builder().append(1); }",
    ) { code, target, newExpr ->
      assertFalse("candidates shown: `$code`", target.showAllCandidates)
      assertEquals("java.lang.StringBuilder", newExpr.containingClass?.qualifiedName)
    }
  }

  fun testAnnotationTarget() {
    val ann = assertSingleTargetElement<PsiClass>(
      "public @D<caret>eprecated String foo(Object o) {}",
      "public @Deprecated<caret> String foo(Object o) {}",
    )
    assertEquals("java.lang.Deprecated", ann.qualifiedName)
  }

  fun testNameValuePairTarget() {
    val method = assertSingleTargetElementWithPrefix<PsiAnnotationMethod>(
      """
      @Target(value={METHOD})
      public @interface Foo { booolean forRemoval() default false; }
      """,
      "public @Foo(<caret>forRemoval = true) String foo(Object o) {}",
    )
    assertEquals("forRemoval", method.name)
  }

  fun testReturnTypeTarget() {
    val cls = assertSingleTargetElement<PsiClass>(
      "public <caret>String foo(Object o) {}",
      "public Str<caret>ing foo(Object o) {}",
      "public String<caret> foo(Object o) {}",
    )
    assertEquals("java.lang.String", cls.qualifiedName)
  }

  fun testParameterTypeTarget() {
    val cls = assertSingleTargetElement<PsiClass>(
      "public String foo(<caret>Object o) {}",
      "public String foo(Obj<caret>ect o) {}",
      "public String foo(Object<caret> o) {}",
    )
    assertEquals("java.lang.Object", cls.qualifiedName)
  }

  fun testParameterNameTarget() {
    val parameter = assertSingleTargetElement<PsiParameter>(
      "public String foo(Object <caret>bar) {}",
      "public String foo(Object b<caret>ar) {}",
      "public String foo(Object bar<caret>) {}",
      "public String foo(Object bar<caret> ) {}",
    )
    assertEquals("bar", parameter.name)
  }

  fun testJavadocParameterTarget() {
    val parameter = assertSingleTargetElement<PsiParameter>(
      "/** @param <caret>a description */ void foo(String a) {}",
    )
    assertEquals("a", parameter.name)
  }

  fun testClassTarget() {
    val cls = assertSingleTargetElementInFile<PsiClass>(
      "A.java",
      "class <caret>A {}",
      "class A<caret> {}",
      "/** test <caret> test */ class A {}",
    )
    assertEquals("A", cls.name)
  }

  fun testNewExpressionTarget() {
    assertSingleTargetElement<PsiNewExpression>(
      "class A { StringBuilder s = new StringBuilder(<caret>); }",
      "class A { StringBuilder s = new StringBuilder(12<caret>3); }",
    )
  }

  fun testFieldTarget() {
    val field = assertSingleTargetElement<PsiField>(
      "int <caret>xx;",
      "int x<caret>x;",
      "int xx<caret>;",
    )
    assertEquals("xx", field.name)
  }

  fun testLocalVariableTarget() {
    val variable = assertSingleTargetElement<PsiLocalVariable>(
      "void foo() { var <caret>x = 1; }",
      "void foo() { var x = 1; <caret>x.hashCode(); }",
    )
    assertEquals("x", variable.name)
  }

  fun testTypeParameterTarget() {
    val tp = assertSingleTargetElementInFile<PsiTypeParameter>(
      "A.java",
      "class A<<caret>T> {}",
    )
    assertEquals("T", tp.name)
  }

  fun testAnnotationMethodTarget() {
    val m = assertSingleTargetElementInFile<PsiAnnotationMethod>(
      "Ann.java",
      "@interface Ann { int <caret>value(); }",
      "@interface Ann { int value<caret>(); }",
    )
    assertEquals("value", m.name)
  }

  fun testEnumConstantTarget() {
    val constant = assertSingleTargetElementInFile<PsiEnumConstant>(
      "E.java",
      "enum E { <caret>RED; }",
    )
    assertEquals("RED", constant.name)
  }

  fun testEnumConstantInitializerTarget() {
    val init = assertSingleTargetElementInFile<PsiEnumConstant>(
      "E.java",
      "enum E { <caret>X{}; }",
      "enum E { X<caret>{}; }",
    )
    assertEquals("X", init.name)
  }

  fun testAnonymousClassTarget() {
    val anon = assertSingleTargetElement<PsiClass>(
      "Runnable r = new Runn<caret>able() { public void run(){} };",
      "Runnable r = new Runnable<caret>() { public void run(){} };",
    )
    assertEquals("java.lang.Runnable", anon.qualifiedName)
  }

  fun testModuleTarget() {
    val module = assertSingleTargetElementInFile<PsiJavaModule>(
      "module-info.java",
      "module <caret>m { }",
      "module m<caret> { }",
      "/** test <caret> test */ module m { }",
    )
    assertEquals("m", module.name)
  }

  fun testClassAccessExpression() {
    val clazz = assertSingleTargetElement<PsiClass>(
      "class A { public A(String s) {} } void main() { new A(<caret>String.class.getName()); }",
      "class A { public A(String s) {} } void main() { new A(String<caret>.class.getName()); }",
    )
    assertEquals("java.lang.String", clazz.qualifiedName)
  }

  fun testPackage() {
    val pack = assertSingleTargetElement<PsiPackage>(
      "package java.ut<caret>il;",
    )
    assertEquals("java.util", pack.qualifiedName)
  }

  fun testPackageInfo() {
    assertSingleTargetElementInFile<PsiPackage>(
      "package-info.java",
      "/** test <caret> test */ package foo.bar;", // IJPL-32515
    )
  }

  fun testSubPackage() {
    val pack = assertSingleTargetElement<PsiPackage>(
      "package ja<caret>va.util;",
    )
    assertEquals("java", pack.qualifiedName)
  }

  fun testNoTargets() {
    assertNoTargetElement(
      "class A { <caret> }",
      "void foo() <caret>{}",
      "void foo() { <caret> }",
    )
  }
}
