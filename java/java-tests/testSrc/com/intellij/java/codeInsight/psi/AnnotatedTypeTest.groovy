// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.psi

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE

class AnnotatedTypeTest extends LightJavaCodeInsightFixtureTestCase {
  private PsiElementFactory factory
  private PsiElement context

  @Override
  void setUp() {
    super.setUp()

    factory = myFixture.javaFacade.elementFactory
    context = myFixture.addClass("""\
      package pkg;

      import java.lang.annotation.*;

      @interface A { }
      @Target(ElementType.TYPE_USE) @interface TA { int value() default 42; }

      class O { class I { } }

      @SuppressWarnings("ExceptionClassNameDoesntEndWithException") class E1 extends Exception { }
      @SuppressWarnings("ExceptionClassNameDoesntEndWithException") class E2 extends Exception { }""".stripIndent())
  }

  @Override
  void tearDown() {
    factory = null
    context = null
    super.tearDown()
  }

  void testPrimitiveArrayType() {
    doTest("@A @TA(1) int @TA(2) [] a", "int[]", "@pkg.TA(1) int @pkg.TA(2) []", "int[]", "@TA int @TA []")
  }

  void testEllipsisType() {
    doTest("@TA int @TA ... p", "int...", "@pkg.TA int @pkg.TA ...", "int...", "@TA int @TA ...")
  }

  void testClassReferenceType() {
    doTest("@A @TA(1) String s", "java.lang.String", "java.lang.@pkg.TA(1) String", "String", "@TA String")
  }

  void testQualifiedClassReferenceType() {
    doTest("@A java.lang.@TA(1) String s", "java.lang.String", "java.lang.@pkg.TA(1) String", "String", "@TA String")
  }

  void testQualifiedPackageClassReferenceType() {
    doTest("@A @TA java.lang.String s", "java.lang.String", "java.lang.String", "String", "String" ) // packages can't have type annotations
  }

  void testPartiallyQualifiedClassReferenceType() {
    doTest("@TA(1) O.@TA(2) I i", "pkg.O.I", "pkg.@pkg.TA(1) O.@pkg.TA(2) I", "I", "@TA I")
  }

  void testCStyleArrayType() {
    doTest("@A @TA(1) String @TA(2) [] f @TA(3) []",
           "java.lang.String[][]", "java.lang.@pkg.TA(1) String @pkg.TA(3) [] @pkg.TA(2) []",
           "String[][]", "@TA String @TA [] @TA []")
  }

  void testCStyleMultiArrayType() {
    doTest("@A @TA(1) String @TA(2) [] @TA(3) [] f @TA(4) [] @TA(5) []",
           "java.lang.String[][][][]", "java.lang.@pkg.TA(1) String @pkg.TA(4) [] @pkg.TA(5) [] @pkg.TA(2) [] @pkg.TA(3) []",
           "String[][][][]", "@TA String @TA [] @TA [] @TA [] @TA []")
  }

  void testWildcardType() {
    doTest("Class<@TA(1) ?> c", "java.lang.Class<?>", "java.lang.Class<@pkg.TA(1) ?>", "Class<?>", "Class<@TA ?>")
  }

  void testWildcardBoundType() {
    doTest("Class<@TA(1) ? extends @TA(2) Object> c",
           "java.lang.Class<? extends java.lang.Object>", "java.lang.Class<@pkg.TA(1) ? extends java.lang.@pkg.TA(2) Object>",
           "Class<? extends Object>", "Class<@TA ? extends @TA Object>")
  }

  void testDisjunctionType() {
    def psi = factory.createStatementFromText("try { } catch (@A @TA(1) E1 | @TA(2) E2 e) { }", context) as PsiTryStatement
    assertTypeText psi.catchBlockParameters[0].type, "pkg.E1 | pkg.E2", "pkg.@pkg.TA(1) E1 | pkg.@pkg.TA(2) E2", "E1 | E2", "@TA E1 | @TA E2"
  }

  void testDiamondType() {
    def psi = factory.createStatementFromText("Class<@TA String> cs = new Class<>()", context) as PsiDeclarationStatement
    def var = psi.declaredElements[0] as PsiVariable
    assertTypeText var.initializer.type,
                   "java.lang.Class<java.lang.String>", "java.lang.Class<java.lang.@pkg.TA String>",
                   "Class<String>", "Class<@TA String>"
  }

  void testImmediateClassType() {
    def aClass = myFixture.javaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT)
    def annotations = factory.createParameterFromText("@TA int x", context).modifierList.annotations
    def type = new PsiImmediateClassType(aClass, PsiSubstitutor.EMPTY, LanguageLevel.JDK_1_8, annotations)
    assertTypeText type, CommonClassNames.JAVA_LANG_OBJECT, "java.lang.@pkg.TA Object", "Object", "@TA Object"
  }

  void testFieldType() {
    def psi = factory.createFieldFromText("@A @TA(1) String f;", context)
    assertTypeText psi.type, "java.lang.String", "java.lang.@pkg.TA(1) String", "String", "@TA String"
    assertAnnotations psi.type, "@TA(1)"
  }

  void testMethodReturnType() {
    def psi = factory.createMethodFromText("@A @TA(1) <T> @TA(2) String m() { return null; }", context)
    assertTypeText psi.returnType, "java.lang.String", "java.lang.@pkg.TA(1) @pkg.TA(2) String", "String", "@TA @TA String"
    assertAnnotations psi.returnType, "@TA(1)", "@TA(2)"
  }

  void testIsAnnotated() {
    def unqualified = factory.createParameterFromText("@A @TA(1) String p", context)
    assert AnnotationUtil.isAnnotated(unqualified, "pkg.A", CHECK_TYPE)
    assert AnnotationUtil.isAnnotated(unqualified, "pkg.TA", CHECK_TYPE)

    def qualified = factory.createParameterFromText("@A java.lang.@TA(1) String p", context)
    assert AnnotationUtil.isAnnotated(qualified, "pkg.A", CHECK_TYPE)
    assert AnnotationUtil.isAnnotated(qualified, "pkg.TA", CHECK_TYPE)
  }


  private void doTest(String text, String canonical, String canonicalAnnotated, String presentable, String presentableAnnotated) {
    assertTypeText factory.createParameterFromText(text, context).type, canonical, canonicalAnnotated, presentable, presentableAnnotated
  }

  private static void assertTypeText(PsiType type, String canonical, String canonicalAnnotated, String presentable, String presentableAnnotated) {
    assert type.getCanonicalText(false) == canonical
    assert type.getCanonicalText(true) == canonicalAnnotated
    assert type.getPresentableText(false) == presentable
    assert type.getPresentableText(true) == presentableAnnotated
  }

  private static void assertAnnotations(PsiType type, String... annotations) {
    assert type.annotations.collect { it.text } == annotations.toList()
  }
}