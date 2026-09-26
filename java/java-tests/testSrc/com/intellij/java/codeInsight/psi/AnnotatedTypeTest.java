// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.psi;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

public class AnnotatedTypeTest extends LightJavaCodeInsightFixtureTestCase {
  private PsiElementFactory factory;
  private PsiElement context;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    factory = myFixture.getJavaFacade().getElementFactory();
    context = myFixture.addClass("""
       package pkg;

       import java.lang.annotation.*;

       @interface A { }
       @Target(ElementType.TYPE_USE) @interface TA { int value() default 42; }

       class O { class I { } }

       @SuppressWarnings("ExceptionClassNameDoesntEndWithException") class E1 extends Exception { }
       @SuppressWarnings("ExceptionClassNameDoesntEndWithException") class E2 extends Exception { }""".stripIndent());
  }

  @Override
  public void tearDown() throws Exception {
    factory = null;
    context = null;
    super.tearDown();
  }

  public void testPrimitiveArrayType() {
    doTest("@A @TA(1) int @TA(2) [] a", "int[]", "@pkg.TA(1) int @pkg.TA(2) []", "int[]", "@TA int @TA []");
  }

  public void testEllipsisType() {
    doTest("@TA int @TA ... p", "int...", "@pkg.TA int @pkg.TA ...", "int...", "@TA int @TA ...");
  }

  public void testClassReferenceType() {
    doTest("@A @TA(1) String s", "java.lang.String", "java.lang.@pkg.TA(1) String", "String", "@TA String");
  }

  public void testQualifiedClassReferenceType() {
    doTest("@A java.lang.@TA(1) String s", "java.lang.String", "java.lang.@pkg.TA(1) String", "String", "@TA String");
  }

  public void testQualifiedPackageClassReferenceType() {
    doTest("@A @TA java.lang.String s", "java.lang.String", "java.lang.String", "String", "String");// packages can't have type annotations
  }

  public void testPartiallyQualifiedClassReferenceType() {
    doTest("@TA(1) O.@TA(2) I i", "pkg.O.I", "pkg.@pkg.TA(1) O.@pkg.TA(2) I", "I", "@TA O.@TA I");
  }

  public void testCStyleArrayType() {
    doTest("@A @TA(1) String @TA(2) [] f @TA(3) []", "java.lang.String[][]", "java.lang.@pkg.TA(1) String @pkg.TA(3) [] @pkg.TA(2) []",
           "String[][]", "@TA String @TA [] @TA []");
  }

  public void testCStyleMultiArrayType() {
    doTest("@A @TA(1) String @TA(2) [] @TA(3) [] f @TA(4) [] @TA(5) []", "java.lang.String[][][][]",
           "java.lang.@pkg.TA(1) String @pkg.TA(4) [] @pkg.TA(5) [] @pkg.TA(2) [] @pkg.TA(3) []", "String[][][][]",
           "@TA String @TA [] @TA [] @TA [] @TA []");
  }

  public void testWildcardType() {
    doTest("Class<@TA(1) ?> c", "java.lang.Class<?>", "java.lang.Class<@pkg.TA(1) ?>", "Class<?>", "Class<@TA ?>");
  }

  public void testWildcardBoundType() {
    doTest("Class<@TA(1) ? extends @TA(2) Object> c", "java.lang.Class<? extends java.lang.Object>",
           "java.lang.Class<@pkg.TA(1) ? extends java.lang.@pkg.TA(2) Object>", "Class<? extends Object>",
           "Class<@TA ? extends @TA Object>");
  }

  public void testDisjunctionType() {
    PsiTryStatement psi = (PsiTryStatement)factory.createStatementFromText("try { } catch (@A @TA(1) E1 | @TA(2) E2 e) { }", context);
    assertTypeText(psi.getCatchBlockParameters()[0].getType(), "pkg.E1 | pkg.E2", "pkg.@pkg.TA(1) E1 | pkg.@pkg.TA(2) E2", "E1 | E2",
                   "@TA E1 | @TA E2");
  }

  public void testDiamondType() {
    PsiDeclarationStatement psi = (PsiDeclarationStatement)factory.createStatementFromText("Class<@TA String> cs = new Class<>()", context);
    PsiVariable var = (PsiVariable)psi.getDeclaredElements()[0];
    assertTypeText(var.getInitializer().getType(), "java.lang.Class<java.lang.String>", "java.lang.Class<java.lang.@pkg.TA String>",
                   "Class<String>", "Class<@TA String>");
  }

  public void testImmediateClassType() {
    PsiClass aClass = myFixture.getJavaFacade().findClass(CommonClassNames.JAVA_LANG_OBJECT);
    PsiAnnotation[] annotations = factory.createParameterFromText("@TA int x", context).getModifierList().getAnnotations();
    PsiImmediateClassType type = new PsiImmediateClassType(aClass, PsiSubstitutor.EMPTY, LanguageLevel.JDK_1_8, annotations);
    assertTypeText(type, CommonClassNames.JAVA_LANG_OBJECT, "java.lang.@pkg.TA Object", "Object", "@TA Object");
  }

  public void testFieldType() {
    PsiField psi = factory.createFieldFromText("@A @TA(1) String f;", context);
    assertTypeText(psi.getType(), "java.lang.String", "java.lang.@pkg.TA(1) String", "String", "@TA String");
    assertAnnotations(psi.getType(), "@TA(1)");
  }

  public void testMethodReturnType() {
    PsiMethod psi = factory.createMethodFromText("@A @TA(1) <T> @TA(2) String m() { return null; }", context);
    assertTypeText(psi.getReturnType(), "java.lang.String", "java.lang.@pkg.TA(1) @pkg.TA(2) String", "String", "@TA @TA String");
    assertAnnotations(psi.getReturnType(), "@TA(1)", "@TA(2)");
  }

  public void testIsAnnotated() {
    PsiParameter unqualified = factory.createParameterFromText("@A @TA(1) String p", context);
    assertTrue(AnnotationUtil.isAnnotated(unqualified, "pkg.A", CHECK_TYPE));
    assertTrue(AnnotationUtil.isAnnotated(unqualified, "pkg.TA", CHECK_TYPE));

    PsiParameter qualified = factory.createParameterFromText("@A java.lang.@TA(1) String p", context);
    assertTrue(AnnotationUtil.isAnnotated(qualified, "pkg.A", CHECK_TYPE));
    assertTrue(AnnotationUtil.isAnnotated(qualified, "pkg.TA", CHECK_TYPE));
  }

  private void doTest(String text, String canonical, String canonicalAnnotated, String presentable, String presentableAnnotated) {
    assertTypeText(factory.createParameterFromText(text, context).getType(), canonical, canonicalAnnotated, presentable,
                   presentableAnnotated);
  }

  private static void assertTypeText(PsiType type,
                                     String canonical,
                                     String canonicalAnnotated,
                                     String presentable,
                                     String presentableAnnotated) {
    assertEquals(canonical, type.getCanonicalText(false));
    assertEquals(canonicalAnnotated, type.getCanonicalText(true));
    assertEquals(presentable, type.getPresentableText(false));
    assertEquals(presentableAnnotated, type.getPresentableText(true));
  }

  private static void assertAnnotations(PsiType type, String... annotations) {
    assertEquals(List.of(annotations), ContainerUtil.map(type.getAnnotations(), PsiAnnotation::getText));
  }
}
