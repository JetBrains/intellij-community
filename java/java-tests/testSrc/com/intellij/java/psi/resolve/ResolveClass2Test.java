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

public class ResolveClass2Test extends LightResolveTestCase {
  public void testFQName() {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testVarInNew() {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testVarInNew1() {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testPrivateInExtends() {
    PsiReference ref = configure();
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertTrue(target instanceof PsiClass);
    assertFalse(result.isAccessible());
  }

  public void testQNew1() {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testInnerPrivateMember1() {
    PsiReference ref = configure();
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertTrue(target instanceof PsiClass);
    assertTrue(result.isValidResult());
  }


  public void testQNew2() {
    PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)configure();
    PsiElement target = ref.advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);

    PsiElement parent = ref.getParent();
    assertTrue(parent instanceof PsiAnonymousClass);
    ((PsiAnonymousClass)parent).getBaseClassType().resolve();

    assertEquals(target, ((PsiAnonymousClass)parent).getBaseClassType().resolve());
  }

  public void testClassExtendsItsInner1() {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
    assertEquals("B.Foo", ((PsiClass)target).getQualifiedName());

    PsiReference refCopy = ref.getElement().copy().getReference();
    assert refCopy != null;
    PsiElement target1 = ((PsiJavaReference)refCopy).advancedResolve(true).getElement();
    assertTrue(target1 instanceof PsiClass);
    //assertNull(target1.getContainingFile().getVirtualFile());
    assertEquals("B.Foo", ((PsiClass)target1).getQualifiedName());
  }

  public void testClassExtendsItsInner2() {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertNull(target);  //[ven] this should not be resolved
    /*assertTrue(target instanceof PsiClass);
    assertEquals("TTT.Bar", ((PsiClass)target).getQualifiedName());*/
  }

  public void testSCR40332() {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertNull(target);
  }

  public void testImportConflict1() {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertNull(target);
  }

  public void testImportConflict2() {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
    assertEquals("java.util.Date", ((PsiClass)target).getQualifiedName());
  }

  public void testLocals1() {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
    // local class
    assertNull(((PsiClass)target).getQualifiedName());
  }

  public void testLocals2() {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
    // local class
    assertNull(((PsiClass)target).getQualifiedName());
  }

  public void testShadowing() {
    PsiReference ref = configure();
    JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    assertTrue(result.getElement() instanceof PsiClass);
    assertFalse(result.isValidResult());
    assertFalse(result.isAccessible());
  }

  public void testStaticImportVsImplicit() {
    PsiReference ref = configure();
    JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    final PsiElement element = result.getElement();
    assertTrue(element instanceof PsiClass);
    assertEquals("Outer.Double", ((PsiClass)element).getQualifiedName());
  }

  public void testQualifiedAnonymousClass() {
    myFixture.addClass("package foo; public class Outer { protected static class Inner { protected Inner() {} } }");
    PsiReference ref = configure();
    
    assertEquals("Inner", assertInstanceOf(ref.resolve(), PsiClass.class).getName());
  }
  
  private PsiReference configure() {
    return findReferenceAtCaret("class/" + getTestName(false) + ".java");
  }
}
