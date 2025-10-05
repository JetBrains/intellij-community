// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.testFramework.IdeaTestUtil;
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
    return findReferenceAtCaret("method/" + getTestName(false) + ".java").resolve();
  }

  private JavaResolveResult advancedResolve() {
    PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)findReferenceAtCaret("method/" + getTestName(false) + ".java");
    return ref.advancedResolve(true);
  }

  public void testShortVsInt() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    PsiParameter parm = ((PsiMethod)target).getParameterList().getParameters()[0];
    assertEquals(PsiTypes.intType(), parm.getType());
  }

  public void testSimple() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertEquals("method", ((PsiMethod) target).getName());
  }

  public void testIndexOf() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    PsiParameter parm = ((PsiMethod)target).getParameterList().getParameters()[0];
    assertEquals(PsiTypes.intType(), parm.getType());
  }

  public void testSuper1() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
  }

  public void testInherit1() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertInstanceOf(target.getParent(), PsiClass.class);
    assertEquals("B", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testNextMethod() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertEquals(PsiTypes.booleanType(), ((PsiMethod)target).getParameterList().getParameters()[1].getType());
  }

  public void testMethodConflict1() {
    PsiElement target = resolve();
    assertNull(target);
  }

  public void testValueOf() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertEquals(PsiTypes.intType(), ((PsiMethod)target).getParameterList().getParameters()[0].getType());
  }

  public void testMethodFromAnonBase() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
  }

  public void testSCR5859() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    PsiParameter[] parms = ((PsiMethod)target).getParameterList().getParameters();
    assertEquals("Derived", parms[0].getType().getPresentableText());
    assertEquals("Derived", parms[1].getType().getPresentableText());
  }

  public void testPreferPublic() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertTrue(((PsiMethod)target).hasModifierProperty(PsiModifier.PUBLIC));
  }

  public void testRemove1() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertInstanceOf(target.getParent(), PsiClass.class);
    assertEquals("Remove1.A", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testRemove2() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertInstanceOf(target.getParent(), PsiClass.class);
    assertEquals("java.util.AbstractCollection", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testArray1() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
  }

  public void testCycle1() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertInstanceOf(target.getParent(), PsiClass.class);
    assertEquals("Cycle1.C", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testAnonymousSuper1() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertInstanceOf(target.getParent(), PsiClass.class);
    assertEquals("Test1.A", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testBug7968() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertInstanceOf(target.getParent(), PsiClass.class);
    assertEquals("Bug7968.Bar", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testInheranceWithExtendsConflict() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertInstanceOf(target.getParent(), PsiClass.class);
    assertEquals("B", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testSout() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertEquals("println", ((PsiMethod)target).getName());
  }

  public void testSCR5134() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    PsiParameter parm = ((PsiMethod)target).getParameterList().getParameters()[0];
    assertEquals("Integer", parm.getType().getPresentableText());
  }

  public void testPartlyImplement1() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertInstanceOf(target.getParent(), PsiClass.class);
    assertEquals("A.Predicate", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testTestOverloading1() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    assertInstanceOf(target.getParent(), PsiClass.class);
    assertEquals("B", ((PsiClass) target.getParent()).getName());
  }


  public void testSuperOfObject() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    final PsiMethod method = (PsiMethod)target;
    assertEquals("clone", method.getName());
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, method.getContainingClass().getQualifiedName());
  }


  public void testStaticVSNonStatic() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    PsiMethod method = (PsiMethod) target;
    assertEquals(0, method.getParameterList().getParametersCount());
  }

  public void testClone() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
  }

  public void testThrowWithoutNew() {
    PsiElement target = resolve();
    assertNull(target);
  }

  public void testThrowWithoutNew2() {
    PsiElement target = advancedResolve().getElement();
    assertInstanceOf(target, PsiClass.class);
  }

  public void testNotAccessibleAccessClass() {
    JavaResolveResult result = advancedResolve();
    assertFalse(result.isAccessible());
  }

  public void testInnerClass() {
    JavaResolveResult result = advancedResolve();
    assertNotNull(result.getElement());
    assertInstanceOf(result,  MethodCandidateInfo.class);
    assertFalse(result.isValidResult());
    assertFalse(((MethodCandidateInfo)result).isApplicable());
    final PsiClass aClass = ((PsiMethod)result.getElement()).getContainingClass();
    assertNotNull(aClass.getContainingClass());
  }

  public void testPrivateInSuperInner() {
    JavaResolveResult result = advancedResolve();
    assertNotNull(result.getElement());
    assertInstanceOf(result,  MethodCandidateInfo.class);
    assertFalse(result.isValidResult());
    assertFalse(result.isStaticsScopeCorrect());
  }

  public void testPrivateInSuperInner1() {
    JavaResolveResult result = advancedResolve();
    assertNotNull(result.getElement());
    assertInstanceOf(result,  MethodCandidateInfo.class);
    assertTrue(result.isValidResult());
  }

  public void testProtectedM() {
    JavaResolveResult result = advancedResolve();
    assertTrue(result.getElement() instanceof PsiMethod);
    assertFalse(result.isAccessible());
  }

  public void testImplementOrder() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    PsiMethod method = (PsiMethod) target;
    assertEquals("II", method.getContainingClass().getName());
  }

  public void testObjectVsInterface() {
    PsiElement target = resolve();
    assertInstanceOf(target, PsiMethod.class);
    PsiMethod method = (PsiMethod) target;
    assertEquals("PublicCloneable", method.getContainingClass().getName());
  }

  public void testSwitchExpressionType() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> {
      PsiMethod target = (PsiMethod)resolve();
      assertEquals(PsiTypes.intType(), target.getParameterList().getParameters()[0].getType());
    });
  }

  public void testMultipleJavadocReference() {
    PsiJavaReference ref = (PsiJavaReference)findReferenceAtCaret("method/" + getTestName(false) + ".java");
    assertSize(3, ref.multiResolve(false));
  }

  public void testMultipleInheritancePathsToMethod() {
    PsiReference ref = findReferenceAtCaret("method/" + getTestName(false) + ".java");

    // just assume this is called by some highlighting inspection/intention/pass before the resolve
    myFixture.findClass("NN").getAllMethods();

    PsiElement target = ref.resolve();
    assertInstanceOf(target, PsiMethod.class);
  }
}
