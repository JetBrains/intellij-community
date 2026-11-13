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
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiReference;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveClass2Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightResolveFixture> resolveFixture = lightResolveFixture(projectFixture);

  @Test
  public void testFQName() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertTrue(target instanceof PsiClass);
    });
  }

  @Test
  public void testVarInNew() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertTrue(target instanceof PsiClass);
    });
  }

  @Test
  public void testVarInNew1() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertTrue(target instanceof PsiClass);
    });
  }

  @Test
  public void testPrivateInExtends() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
      PsiElement target = result.getElement();
      assertTrue(target instanceof PsiClass);
      assertFalse(result.isAccessible());
    });
  }

  @Test
  public void testQNew1() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertTrue(target instanceof PsiClass);
    });
  }

  @Test
  public void testInnerPrivateMember1() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
      PsiElement target = result.getElement();
      assertTrue(target instanceof PsiClass);
      assertTrue(result.isValidResult());
    });
  }

  @Test
  public void testQNew2() {
    PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.advancedResolve(true).getElement();
      assertTrue(target instanceof PsiClass);

      PsiElement parent = ref.getParent();
      assertTrue(parent instanceof PsiAnonymousClass);
      ((PsiAnonymousClass)parent).getBaseClassType().resolve();

      assertEquals(target, ((PsiAnonymousClass)parent).getBaseClassType().resolve());
    });
  }

  @Test
  public void testClassExtendsItsInner1() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertTrue(target instanceof PsiClass);
      assertEquals("B.Foo", ((PsiClass)target).getQualifiedName());

      PsiReference refCopy = ref.getElement().copy().getReference();
      assert refCopy != null;
      PsiElement target1 = ((PsiJavaReference)refCopy).advancedResolve(true).getElement();
      assertTrue(target1 instanceof PsiClass);
      //assertNull(target1.getContainingFile().getVirtualFile());
      assertEquals("B.Foo", ((PsiClass)target1).getQualifiedName());
    });
  }

  @Test
  public void testClassExtendsItsInner2() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertNull(target);  //[ven] this should not be resolved
      /*assertTrue(target instanceof PsiClass);
      assertEquals("TTT.Bar", ((PsiClass)target).getQualifiedName());*/
    });
  }

  @Test
  public void testSCR40332() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertNull(target);
    });
  }

  @Test
  public void testImportConflict1() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertNull(target);
    });
  }

  @Test
  public void testImportConflict2() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertTrue(target instanceof PsiClass);
      assertEquals("java.util.Date", ((PsiClass)target).getQualifiedName());
    });
  }

  @Test
  public void testLocals1() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertTrue(target instanceof PsiClass);
      // local class
      assertNull(((PsiClass)target).getQualifiedName());
    });
  }

  @Test
  public void testLocals2() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertTrue(target instanceof PsiClass);
      // local class
      assertNull(((PsiClass)target).getQualifiedName());
    });
  }

  @Test
  public void testShadowing() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
      assertTrue(result.getElement() instanceof PsiClass);
      assertFalse(result.isValidResult());
      assertFalse(result.isAccessible());
    });
  }

  @Test
  public void testStaticImportVsImplicit() {
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
      final PsiElement element = result.getElement();
      assertTrue(element instanceof PsiClass);
      assertEquals("Outer.Double", ((PsiClass)element).getQualifiedName());
    });
  }

  @Test
  public void testQualifiedAnonymousClass() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    myFixture.addClass("package foo; public class Outer { protected static class Inner { protected Inner() {} } }");
    PsiReference ref = configure();
    
    ReadAction.runBlocking(() -> {
      assertEquals("Inner", assertInstanceOf(PsiClass.class, ref.resolve()).getName());
    });
  }
  
  private PsiReference configure() {
    return resolveFixture.get().findReferenceAtCaret("class/" + resolveFixture.get().getTestName(false) + ".java");
  }
}
