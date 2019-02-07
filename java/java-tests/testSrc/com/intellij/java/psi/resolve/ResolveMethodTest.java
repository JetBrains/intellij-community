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
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.LightResolveTestCase;
import org.jetbrains.annotations.NotNull;

public class ResolveMethodTest extends LightResolveTestCase {
  
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_4;
  }

  private PsiElement resolve() {
    PsiReference ref = findReferenceAtCaret("method/" + getTestName(false) + ".java");
    return ref.resolve();
  }

  private JavaResolveResult advancedResolve() {
    PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)findReferenceAtCaret("method/" + getTestName(false) + ".java");
    return ref.advancedResolve(true);
  }

  public void testShortVsInt() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiParameter parm = ((PsiMethod)target).getParameterList().getParameters()[0];
    assertEquals(PsiType.INT, parm.getType());
  }

  public void testSimple() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals("method", ((PsiMethod) target).getName());
  }

  public void testIndexOf() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiParameter parm = ((PsiMethod)target).getParameterList().getParameters()[0];
    assertEquals(PsiType.INT, parm.getType());
  }

  public void testSuper1() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
  }

  public void testInherit1() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("B", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testNextMethod() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals(PsiType.BOOLEAN, ((PsiMethod)target).getParameterList().getParameters()[1].getType());
  }

  public void testMethodConflict1() {
    PsiElement target = resolve();
    assertNull(target);
  }

  public void testValueOf() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals(PsiType.INT, ((PsiMethod)target).getParameterList().getParameters()[0].getType());
  }

  public void testMethodFromAnonBase() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
  }

  public void testSCR5859() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiParameter[] parms = ((PsiMethod)target).getParameterList().getParameters();
    assertEquals("Derived", parms[0].getType().getPresentableText());
    assertEquals("Derived", parms[1].getType().getPresentableText());
  }

  public void testPreferPublic() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(((PsiMethod)target).hasModifierProperty(PsiModifier.PUBLIC));
  }

  public void testRemove1() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("Remove1.A", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testRemove2() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("java.util.AbstractCollection", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testArray1() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
  }

  public void testCycle1() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("Cycle1.C", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testAnonymousSuper1() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("Test1.A", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testBug7968() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("Bug7968.Bar", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testInheranceWithExtendsConflict() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("B", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testSout() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals("println", ((PsiMethod)target).getName());
  }

  /*
  public void testSCR5134() throws Exception{
    PsiReference ref = configureByFile("method/SCR5134.java");
    PsiElement target = ref.resolve();
    assertTrue(target instanceof PsiMethod);
    PsiParameter parm = ((PsiMethod)target).getParameterList().getParameters()[0];
    assertTrue(parm.getType().getText().equals("Integer"));
  }
  */

  public void testPartlyImplement1() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("A.Predicate", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testTestOverloading1() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("B", ((PsiClass) target.getParent()).getName());
  }


  public void testSuperOfObject() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    final PsiMethod method = (PsiMethod)target;
    assertEquals("clone", method.getName());
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, method.getContainingClass().getQualifiedName());
  }


  public void testStaticVSNonStatic() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiMethod method = (PsiMethod) target;
    assertEquals(0, method.getParameterList().getParametersCount());
  }

  public void testClone() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
  }

  public void testThrowWithoutNew() {
    PsiElement target = resolve();
    assertNull(target);
  }

  public void testThrowWithoutNew2() {
    PsiElement target = advancedResolve().getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testNotAccessibleAccessClass() {
    JavaResolveResult result = advancedResolve();
    assertFalse(result.isAccessible());
  }

  public void testInnerClass() {
    JavaResolveResult result = advancedResolve();
    assertNotNull(result.getElement());
    assertTrue(result instanceof MethodCandidateInfo);
    assertFalse(result.isValidResult());
    assertFalse(((MethodCandidateInfo)result).isApplicable());
    final PsiClass aClass = ((PsiMethod)result.getElement()).getContainingClass();
    assertNotNull(aClass.getContainingClass());
  }

  public void testPrivateInSuperInner() {
    JavaResolveResult result = advancedResolve();
    assertNotNull(result.getElement());
    assertTrue(result instanceof MethodCandidateInfo);
    assertFalse(result.isValidResult());
    assertFalse(result.isStaticsScopeCorrect());
  }

  public void testPrivateInSuperInner1() {
    JavaResolveResult result = advancedResolve();
    assertNotNull(result.getElement());
    assertTrue(result instanceof MethodCandidateInfo);
    assertTrue(result.isValidResult());
  }

  public void testProtectedM() {
    JavaResolveResult result = advancedResolve();
    assertTrue(result.getElement() instanceof PsiMethod);
    assertFalse(result.isAccessible());
  }

  // This test complile but it seems to be a bug.
  //public void testDependingParams1() throws Exception{
  //  PsiJavaReference ref = (PsiJavaReference) configureByFile("method/generics/DependingParams.java");
  //  final JavaResolveResult result = ref.advancedResolve(true);
  //  assertTrue(result.isValidResult());
  //}

  public void testImplementOrder() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiMethod method = (PsiMethod) target;
    assertEquals("II", method.getContainingClass().getName());
  }

  public void testObjectVsInterface() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiMethod method = (PsiMethod) target;
    assertEquals("PublicCloneable", method.getContainingClass().getName());
  }

  public void testMultipleInheritancePathsToMethod() {
    PsiReference ref = findReferenceAtCaret("method/" + getTestName(false) + ".java");

    // just assume this is called by some highlighting inspection/intention/pass before the resolve
    myFixture.findClass("NN").getAllMethods();

    PsiElement target = ref.resolve();
    assertInstanceOf(target, PsiMethod.class);
  }
}
