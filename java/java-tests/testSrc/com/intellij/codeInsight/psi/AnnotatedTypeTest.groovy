/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.testFramework.LightIdeaTestCase

@SuppressWarnings("GroovyAssignabilityCheck")
class AnnotatedTypeTest extends LightIdeaTestCase {
  private PsiFile context
  private PsiElementFactory factory

  public void setUp() throws Exception {
    super.setUp()
    factory = javaFacade.elementFactory
    context = createFile("typeCompositionTest.java", """
package pkg;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@interface A { }
@Target({TYPE_USE}) @interface TA { int value() default 42; }

class E1 extends Exception { }
class E2 extends Exception { }
""")
  }

  public void testPrimitiveArrayType() {
    doTest("@A @TA(1) int @TA(2) [] a", "@pkg.TA(1) int @pkg.TA(2) []", "int[]")
  }

  public void testEllipsisType() {
    def psi = factory.createParameterFromText("@TA int @TA ... p", context)
    assertTypeText(psi.type, "@pkg.TA int @pkg.TA ...", "int...")
  }

  public void testClassReferenceType() {
    doTest("@A @TA(1) String s", "java.lang.@pkg.TA(1) String", "java.lang.String")
    doTest("@A java.lang.@TA(1) String s", "java.lang.@pkg.TA(1) String", "java.lang.String")
  }

  public void testCStyleArrayType() {
    doTest("@A @TA(1) String @TA(2) [] f @TA(3) []", "java.lang.@pkg.TA(1) String @pkg.TA(2) [] @pkg.TA(3) []", "java.lang.String[][]")
  }

  public void testWildcardType() {
    doTest("Class<@TA(1) ?> c", "java.lang.Class<@pkg.TA(1) ?>", "java.lang.Class<?>")
  }

  public void testDisjunctionType() {
    def psi = factory.createStatementFromText("try { } catch (@A @TA(1) E1 | @TA(2) E2 e) { }", context)
    assertTypeText(psi.catchBlockParameters[0].type, "pkg.@pkg.TA(1) E1 | pkg.@pkg.TA(2) E2", "pkg.E1 | pkg.E2")
  }

  public void testDiamondType() {
    def psi = factory.createStatementFromText("Class<@TA String> cs = new Class<>()", context)
    assertTypeText(psi.declaredElements[0].initializer.type, "java.lang.Class<java.lang.@pkg.TA String>", "java.lang.Class<java.lang.String>")
  }

  public void testImmediateClassType() {
    def aClass = javaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT)
    def statement = factory.createStatementFromText("@TA int x", context)
    def annotations = statement.declaredElements[0].modifierList.annotations
    def type = new PsiImmediateClassType(aClass, PsiSubstitutor.EMPTY, LanguageLevel.JDK_1_8, annotations)
    assertTypeText(type, "java.lang.@pkg.TA Object", CommonClassNames.JAVA_LANG_OBJECT)
  }

  private void doTest(String text, String annotated, String canonical) {
    def psi = factory.createStatementFromText(text, context)
    assertTypeText(psi.declaredElements[0].type, annotated, canonical)
  }

  private static void assertTypeText(PsiType type, String annotated, String canonical) {
    assert type.getCanonicalText(true) == annotated
    assert type.getCanonicalText(false) == canonical
  }
}
