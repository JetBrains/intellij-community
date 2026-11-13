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

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.LightResolveFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.LightResolveFixtureKt.lightResolveFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@HeavyPlatformTestCase.WrapInCommand
@TestApplication
public class ResolveVariableTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightResolveFixture> resolveFixture = lightResolveFixture(projectFixture);

  @Test
  public void testAnonymousConstructorArg() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiParameter);
      assertEquals("value", ((PsiParameter)target).getName());
    });
  }

  @Test
  public void testLocalVariable1() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiLocalVariable);
      assertEquals("value", ((PsiLocalVariable)target).getName());
    });
  }

  @Test
  public void testVisibility1() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiField);
      assertEquals("variable", ((PsiField)target).getName());
    });
  }

  @Test
  public void testVisibility2() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiField);
      assertEquals("a", ((PsiField)target).getName());
    });
  }

  private PsiElement configureAndResolve() {
    PsiReference ref = configure();
    return ReadAction.computeBlocking(() -> ref.resolve());
  }

  @Test
  public void testVisibility3() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiField);
      assertEquals("i", ((PsiField)target).getName());
    });
  }

  @Test
  public void testVisibility4() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertEquals("a", ((PsiLocalVariable)target).getName());
    });
  }

  @Test
  public void testQualified1() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiField);
      assertEquals("a", ((PsiField)target).getName());
    });
  }

  @Test
  public void testQualified2() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiField);
      assertEquals("a", ((PsiField)target).getName());
    });
  }

  @Test
  public void testQualified3() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiField);
      assertEquals("a", ((PsiField)target).getName());
    });
  }

  @Test
  public void testQualified4() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiField);
      assertEquals("a", ((PsiField)target).getName());
    });
  }

  @Test
  public void testUnresolved1() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNull(target);
    });
  }

  @Test
  public void testFieldFromInterface() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiField);
    });
  }

  @Test
  public void testInterfaceConflict1() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNull(target);
    });
  }

  @Test
  public void testInterfaceConflict2() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNull(target);
    });
  }

  @Test
  public void testInterfaceConflict3() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNull(target);
    });
  }

  @Test
  public void testInterfaceConflict4() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNull(target);
    });
  }

  // This is a bug but it's too hard to fix this :(
  // TODO: try to fix
  public void dontTestInterfaceConflict5() {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  @Test
  public void testInterfaceConflict6() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNull(target);
    });
  }

  @Test
  public void testInterfaceConflict7() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNotNull(target);
    });
  }

  @Test
  public void testInterfaceConflict8() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNotNull(target);
    });
  }

  @Test
  public void testInterfaceConflict9() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNull(target);
    });
  }

  @Test
  public void testInterfaceConflict10() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNull(target);
    });
  }

  @Test
  public void testArray1() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNotNull(target);
    });
  }

  @Test
  public void testInterfaceConflict11() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNotNull(target);
      assertTrue(target instanceof PsiField);
      assertEquals("B", ((PsiField)target).getContainingClass().getName());
    });
  }

  @Test
  public void testBug7869() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNotNull(target);
    });
  }

  @Test
  public void testInner1() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertNotNull(target);
      assertEquals("Inner1", ((PsiField)target).getContainingClass().getName());
    });
  }

  @Test
  public void testFieldsAndLocals() {
    PsiElement target = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiLocalVariable);
    });
  }

  @Test
  public void testPrivateOverloading() {
    PsiReference ref = resolveFixture.get().findReferenceAtCaret("var/PrivateOverloading.java");
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
      PsiElement target = result.getElement();
      assertNotNull(target);
      assertFalse(result.isValidResult());
    });
  }

  @Test
  public void testVisibility6() {
    PsiReference ref = resolveFixture.get().findReferenceAtCaret("var/Visibility6.java");
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
      PsiElement target = result.getElement();
      assertNotNull(target);
      assertFalse(result.isValidResult());
    });
  }

  @Test
  public void testVisibility7() {
    PsiReference ref = resolveFixture.get().findReferenceAtCaret("var/InnerPrivates1.java");
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
      PsiElement target = result.getElement();
      assertNotNull(target);
      assertTrue(result.isValidResult());
    });
  }

  @Test
  public void testForeachParameter() {
    final PsiReference ref = resolveFixture.get().findReferenceAtCaret("var/ForeachParameter.java");
    ReadAction.runBlocking(() -> {
      final PsiElement element = ref.resolve();
      assertTrue(element instanceof PsiParameter);
      assertEquals("o", ((PsiParameter)element).getName());
      assertTrue(element.getParent() instanceof PsiForeachStatement);
    });
  }

  @Test
  public void testRefInSuper() {
    final PsiJavaReference ref = (PsiJavaReference)resolveFixture.get().findReferenceAtCaret("var/RefInSuper.java");
    ReadAction.runBlocking(() -> {
      final JavaResolveResult resolveResult = ref.advancedResolve(false);
      assertTrue(resolveResult.isValidResult());
      PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
      assertTrue(currentFileResolveScope instanceof PsiClass);
      assertEquals("Inner", ((PsiClass)currentFileResolveScope).getName());
      PsiElement element = resolveResult.getElement();
      assertTrue(element instanceof PsiField);
      assertEquals("i", ((PsiField)element).getName());
    });
  }

  @Test
  public void testRefInOuter() {
    final PsiJavaReference ref = (PsiJavaReference)resolveFixture.get().findReferenceAtCaret("var/RefInOuter.java");
    ReadAction.runBlocking(() -> {
      final JavaResolveResult resolveResult = ref.advancedResolve(false);
      assertTrue(resolveResult.isValidResult());
      PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
      assertTrue(currentFileResolveScope instanceof PsiClass);
      assertEquals("Outer", ((PsiClass)currentFileResolveScope).getName());
      PsiElement element = resolveResult.getElement();
      assertTrue(element instanceof PsiField);
      assertEquals("i", ((PsiField)element).getName());
    });
  }

  @Test
  public void testInheritedOuter() {
    final PsiJavaReference ref = (PsiJavaReference)resolveFixture.get().findReferenceAtCaret("var/InheritedOuter.java");
    ReadAction.runBlocking(() -> {
      final JavaResolveResult resolveResult = ref.advancedResolve(false);
      assertTrue(resolveResult.isValidResult());
    });
  }

  @Test
  public void testStaticFieldsInInterfacesConflict() {
    final PsiJavaReference ref = (PsiJavaReference)resolveFixture.get().findReferenceAtCaret("var/FieldConflict.java");
    ReadAction.runBlocking(() -> {
      final JavaResolveResult resolveResult = ref.advancedResolve(false);
      assertFalse(resolveResult.isValidResult());
      final JavaResolveResult[] results = ref.multiResolve(false);
      assertEquals(2, results.length);
    });
  }

  @Test
  public void testShadowFieldsInHierarchy() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    final PsiJavaReference ref = (PsiJavaReference)configure();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult resolveResult = ref.advancedResolve(false);
      assertTrue(resolveResult.isValidResult());
      PsiField field = myFixture.findClass("TestPage").getFields()[0];
      assertEquals(field, resolveResult.getElement());
    });
  }

  @Test
  public void testShadowFieldsInHierarchy2() {
    PsiElement ref = configureAndResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(ref instanceof PsiField);
      assertEquals("E", ((PsiField)ref).getContainingClass().getName());
    });
  }

  private PsiReference configure() {
    return resolveFixture.get().findReferenceAtCaret("var/" + resolveFixture.get().getTestName(false) + ".java");
  }
}
