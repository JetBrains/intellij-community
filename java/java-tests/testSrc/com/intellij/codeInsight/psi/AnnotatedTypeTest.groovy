/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.psi

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class AnnotatedTypeTest extends LightCodeInsightFixtureTestCase {
  private PsiElementFactory factory
  private PsiElement context

  public void setUp() {
    super.setUp()

    factory = myFixture.javaFacade.elementFactory
    context = myFixture.addClass("""\
      package pkg;

      import java.lang.annotation.*;

      @interface A { }
      @Target(ElementType.TYPE_USE) @interface TA { int value() default 42; }

      class O {
        class I { }
      }

      class E1 extends Exception { }
      class E2 extends Exception { }""".stripIndent())
  }

  public void testPrimitiveArrayType() {
    doTest("@A @TA(1) int @TA(2) [] a", "@pkg.TA(1) int @pkg.TA(2) []", "int[]")
  }

  public void testEllipsisType() {
    doTest("@TA int @TA ... p", "@pkg.TA int @pkg.TA ...", "int...")
  }

  public void testClassReferenceType() {
    doTest("@A @TA(1) String s", "java.lang.@pkg.TA(1) String", "java.lang.String")
  }

  public void testQualifiedClassReferenceType() {
    doTest("@A java.lang.@TA(1) String s", "java.lang.@pkg.TA(1) String", "java.lang.String")
  }

  public void testQualifiedPackageClassReferenceType() {
    doTest("@A @TA java.lang.String s", "java.lang.String", "java.lang.String")  // packages cannot have type annotations
  }

  public void testPartiallyQualifiedClassReferenceType() {
    doTest("@TA(1) O.@TA(2) I i", "pkg.@pkg.TA(1) O.@pkg.TA(2) I", "pkg.O.I")
  }

  public void testCStyleArrayType() {
    doTest("@A @TA(1) String @TA(2) [] f @TA(3) []", "java.lang.@pkg.TA(1) String @pkg.TA(2) [] @pkg.TA(3) []", "java.lang.String[][]")
  }

  public void testWildcardType() {
    doTest("Class<@TA(1) ?> c", "java.lang.Class<@pkg.TA(1) ?>", "java.lang.Class<?>")
  }

  public void testDisjunctionType() {
    def psi = factory.createStatementFromText("try { } catch (@A @TA(1) E1 | @TA(2) E2 e) { }", context) as PsiTryStatement
    assertTypeText(psi.catchBlockParameters[0].type, "pkg.@pkg.TA(1) E1 | pkg.@pkg.TA(2) E2", "pkg.E1 | pkg.E2")
  }

  public void testDiamondType() {
    def psi = factory.createStatementFromText("Class<@TA String> cs = new Class<>()", context) as PsiDeclarationStatement
    def var = psi.declaredElements[0] as PsiVariable
    assertTypeText(var.initializer.type, "java.lang.Class<java.lang.@pkg.TA String>", "java.lang.Class<java.lang.String>")
  }

  public void testImmediateClassType() {
    def aClass = myFixture.javaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT)
    def annotations = factory.createParameterFromText("@TA int x", context).modifierList.annotations
    def type = new PsiImmediateClassType(aClass, PsiSubstitutor.EMPTY, LanguageLevel.JDK_1_8, annotations)
    assertTypeText(type, "java.lang.@pkg.TA Object", CommonClassNames.JAVA_LANG_OBJECT)
  }

  private void doTest(String text, String annotated, String canonical) {
    assertTypeText(factory.createParameterFromText(text, context).type, annotated, canonical)
  }

  private static void assertTypeText(PsiType type, String annotated, String canonical) {
    assert type.getCanonicalText(true) == annotated
    assert type.getCanonicalText(false) == canonical
  }
}