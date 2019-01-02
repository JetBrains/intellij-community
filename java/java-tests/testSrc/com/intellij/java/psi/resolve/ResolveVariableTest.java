/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi.resolve;

import com.intellij.psi.*;
import com.intellij.testFramework.LightResolveTestCase;
import com.intellij.testFramework.PlatformTestCase;

@PlatformTestCase.WrapInCommand
public class ResolveVariableTest extends LightResolveTestCase {
  public void testAnonymousConstructorArg() {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiParameter);
    assertEquals("value", ((PsiParameter)target).getName());
  }

  public void testLocalVariable1() {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiLocalVariable);
    assertEquals("value", ((PsiLocalVariable)target).getName());
  }

  public void testVisibility1() {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("variable", ((PsiField)target).getName());
  }

  public void testVisibility2() {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("a", ((PsiField)target).getName());
  }

  private PsiElement configureAndResolve() {
    PsiReference ref = configure();
    return ref.resolve();
  }

  public void testVisibility3() {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("i", ((PsiField)target).getName());
  }

  public void testVisibility4() {
    PsiElement target = configureAndResolve();
    assertEquals("a", ((PsiLocalVariable)target).getName());
    assertTrue(target instanceof PsiLocalVariable);
  }

  public void testQualified1() {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("a", ((PsiField)target).getName());
  }

  public void testQualified2() {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("a", ((PsiField)target).getName());
  }

  public void testQualified3() {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("a", ((PsiField)target).getName());
  }

  public void testQualified4() {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("a", ((PsiField)target).getName());
  }

  public void testUnresolved1() {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testFieldFromInterface() {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
  }

  public void testInterfaceConflict1() {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testInterfaceConflict2() {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testInterfaceConflict3() {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testInterfaceConflict4() {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  // This is a bug but it's too hard to fix this :(
  // TODO: try to fix
  public void dontTestInterfaceConflict5() {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testInterfaceConflict6() {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testInterfaceConflict7() {
    PsiElement target = configureAndResolve();
    assertNotNull(target);
  }

  public void testInterfaceConflict8() {
    PsiElement target = configureAndResolve();
    assertNotNull(target);
  }

  public void testInterfaceConflict9() {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testInterfaceConflict10() {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testArray1() {
    PsiElement target = configureAndResolve();
    assertNotNull(target);
  }

  public void testInterfaceConflict11() {
    PsiElement target = configureAndResolve();
    assertNotNull(target);
    assertTrue(target instanceof PsiField);
    assertEquals("B", ((PsiField)target).getContainingClass().getName());
  }

  public void testBug7869() {
    PsiElement target = configureAndResolve();
    assertNotNull(target);
  }

  public void testInner1() {
    PsiElement target = configureAndResolve();
    assertNotNull(target);
    assertEquals("Inner1", ((PsiField)target).getContainingClass().getName());
  }

  public void testFieldsAndLocals() {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiLocalVariable);
  }

  public void testPrivateOverloading() {
    PsiReference ref = findReferenceAtCaret("var/PrivateOverloading.java");
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertNotNull(target);
    assertFalse(result.isValidResult());
  }

  public void testVisibility6() {
    PsiReference ref = findReferenceAtCaret("var/Visibility6.java");
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertNotNull(target);
    assertFalse(result.isValidResult());
  }

  public void testVisibility7() {
    PsiReference ref = findReferenceAtCaret("var/InnerPrivates1.java");
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertNotNull(target);
    assertTrue(result.isValidResult());
  }

  public void testForeachParameter() {
    final PsiReference ref = findReferenceAtCaret("var/ForeachParameter.java");
    final PsiElement element = ref.resolve();
    assertTrue(element instanceof PsiParameter);
    assertEquals("o", ((PsiParameter)element).getName());
    assertTrue(element.getParent() instanceof PsiForeachStatement);
  }

  public void testRefInSuper() {
    final PsiJavaReference ref = (PsiJavaReference)findReferenceAtCaret("var/RefInSuper.java");
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(resolveResult.isValidResult());
    PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
    assertTrue(currentFileResolveScope instanceof PsiClass);
    assertEquals("Inner", ((PsiClass)currentFileResolveScope).getName());
    PsiElement element = resolveResult.getElement();
    assertTrue(element instanceof PsiField);
    assertEquals("i", ((PsiField)element).getName());
  }

  public void testRefInOuter() {
    final PsiJavaReference ref = (PsiJavaReference)findReferenceAtCaret("var/RefInOuter.java");
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(resolveResult.isValidResult());
    PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
    assertTrue(currentFileResolveScope instanceof PsiClass);
    assertEquals("Outer", ((PsiClass)currentFileResolveScope).getName());
    PsiElement element = resolveResult.getElement();
    assertTrue(element instanceof PsiField);
    assertEquals("i", ((PsiField)element).getName());
  }

  public void testInheritedOuter() {
    final PsiJavaReference ref = (PsiJavaReference)findReferenceAtCaret("var/InheritedOuter.java");
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(resolveResult.isValidResult());
  }

  public void testStaticFieldsInInterfacesConflict() {
    final PsiJavaReference ref = (PsiJavaReference)findReferenceAtCaret("var/FieldConflict.java");
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertFalse(resolveResult.isValidResult());
    final JavaResolveResult[] results = ref.multiResolve(false);
    assertEquals(2, results.length);
  }

  public void testShadowFieldsInHierarchy() {
    final PsiJavaReference ref = (PsiJavaReference)configure();
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(resolveResult.isValidResult());
    PsiField field = myFixture.findClass("TestPage").getFields()[0];
    assertEquals(field, resolveResult.getElement());
  }

  public void testShadowFieldsInHierarchy2() {
    PsiElement ref = configureAndResolve();
    assertTrue(ref instanceof PsiField);
    assertEquals("E", ((PsiField)ref).getContainingClass().getName());
  }

  private PsiReference configure() {
    return findReferenceAtCaret("var/" + getTestName(false) + ".java");
  }
}
