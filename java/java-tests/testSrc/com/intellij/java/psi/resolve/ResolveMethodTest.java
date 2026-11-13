// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightResolveFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.LightResolveFixtureKt.lightResolveFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveMethodTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_4);
  private final TestFixture<LightResolveFixture> resolveFixture = lightResolveFixture(projectFixture);

  private PsiElement resolve() {
    PsiReference ref = resolveFixture.get().findReferenceAtCaret("method/" + resolveFixture.get().getTestName(false) + ".java");
    return ReadAction.computeBlocking(() -> ref.resolve());
  }

  private JavaResolveResult advancedResolve() {
    PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)resolveFixture.get().findReferenceAtCaret("method/" + resolveFixture.get().getTestName(false) + ".java");
    return ReadAction.computeBlocking(() -> ref.advancedResolve(true));
  }

  @Test
  public void testShortVsInt() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      PsiParameter parm = ((PsiMethod)target).getParameterList().getParameters()[0];
      assertEquals(PsiTypes.intType(), parm.getType());
    });
  }

  @Test
  public void testSimple() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertEquals("method", ((PsiMethod) target).getName());
    });
  }

  @Test
  public void testIndexOf() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      PsiParameter parm = ((PsiMethod)target).getParameterList().getParameters()[0];
      assertEquals(PsiTypes.intType(), parm.getType());
    });
  }

  @Test
  public void testSuper1() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
    });
  }

  @Test
  public void testInherit1() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertInstanceOf(PsiClass.class, target.getParent());
      assertEquals("B", ((PsiClass)target.getParent()).getQualifiedName());
    });
  }

  @Test
  public void testNextMethod() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertEquals(PsiTypes.booleanType(), ((PsiMethod)target).getParameterList().getParameters()[1].getType());
    });
  }

  @Test
  public void testMethodConflict1() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertNull(target);
    });
  }

  @Test
  public void testValueOf() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertEquals(PsiTypes.intType(), ((PsiMethod)target).getParameterList().getParameters()[0].getType());
    });
  }

  @Test
  public void testMethodFromAnonBase() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
    });
  }

  @Test
  public void testSCR5859() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      PsiParameter[] parms = ((PsiMethod)target).getParameterList().getParameters();
      assertEquals("Derived", parms[0].getType().getPresentableText());
      assertEquals("Derived", parms[1].getType().getPresentableText());
    });
  }

  @Test
  public void testPreferPublic() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertTrue(((PsiMethod)target).hasModifierProperty(PsiModifier.PUBLIC));
    });
  }

  @Test
  public void testRemove1() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertInstanceOf(PsiClass.class, target.getParent());
      assertEquals("Remove1.A", ((PsiClass)target.getParent()).getQualifiedName());
    });
  }

  @Test
  public void testRemove2() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertInstanceOf(PsiClass.class, target.getParent());
      assertEquals("java.util.AbstractCollection", ((PsiClass)target.getParent()).getQualifiedName());
    });
  }

  @Test
  public void testArray1() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
    });
  }

  @Test
  public void testCycle1() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertInstanceOf(PsiClass.class, target.getParent());
      assertEquals("Cycle1.C", ((PsiClass)target.getParent()).getQualifiedName());
    });
  }

  @Test
  public void testAnonymousSuper1() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertInstanceOf(PsiClass.class, target.getParent());
      assertEquals("Test1.A", ((PsiClass)target.getParent()).getQualifiedName());
    });
  }

  @Test
  public void testBug7968() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertInstanceOf(PsiClass.class, target.getParent());
      assertEquals("Bug7968.Bar", ((PsiClass)target.getParent()).getQualifiedName());
    });
  }

  @Test
  public void testInheranceWithExtendsConflict() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertInstanceOf(PsiClass.class, target.getParent());
      assertEquals("B", ((PsiClass)target.getParent()).getQualifiedName());
    });
  }

  @Test
  public void testSout() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertEquals("println", ((PsiMethod)target).getName());
    });
  }

  @Test
  public void testSCR5134() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      PsiParameter parm = ((PsiMethod)target).getParameterList().getParameters()[0];
      assertEquals("Integer", parm.getType().getPresentableText());
    });
  }

  @Test
  public void testPartlyImplement1() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertInstanceOf(PsiClass.class, target.getParent());
      assertEquals("A.Predicate", ((PsiClass)target.getParent()).getQualifiedName());
    });
  }

  @Test
  public void testTestOverloading1() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      assertInstanceOf(PsiClass.class, target.getParent());
      assertEquals("B", ((PsiClass) target.getParent()).getName());
    });
  }

  @Test
  public void testSuperOfObject() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      final PsiMethod method = (PsiMethod)target;
      assertEquals("clone", method.getName());
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, method.getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testStaticVSNonStatic() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      PsiMethod method = (PsiMethod) target;
      assertEquals(0, method.getParameterList().getParametersCount());
    });
  }

  @Test
  public void testClone() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
    });
  }

  @Test
  public void testThrowWithoutNew() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertNull(target);
    });
  }

  @Test
  public void testThrowWithoutNew2() {
    PsiElement target = advancedResolve().getElement();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiClass.class, target);
    });
  }

  @Test
  public void testNotAccessibleAccessClass() {
    JavaResolveResult result = advancedResolve();
    ReadAction.runBlocking(() -> {
      assertFalse(result.isAccessible());
    });
  }

  @Test
  public void testInnerClass() {
    JavaResolveResult result = advancedResolve();
    ReadAction.runBlocking(() -> {
      assertNotNull(result.getElement());
      assertInstanceOf(MethodCandidateInfo.class, result);
      assertFalse(result.isValidResult());
      assertFalse(((MethodCandidateInfo)result).isApplicable());
      final PsiClass aClass = ((PsiMethod)result.getElement()).getContainingClass();
      assertNotNull(aClass.getContainingClass());
    });
  }

  @Test
  public void testPrivateInSuperInner() {
    JavaResolveResult result = advancedResolve();
    ReadAction.runBlocking(() -> {
      assertNotNull(result.getElement());
      assertInstanceOf(MethodCandidateInfo.class, result);
      assertFalse(result.isValidResult());
      assertFalse(result.isStaticsScopeCorrect());
    });
  }

  @Test
  public void testPrivateInSuperInner1() {
    JavaResolveResult result = advancedResolve();
    ReadAction.runBlocking(() -> {
      assertNotNull(result.getElement());
      assertInstanceOf(MethodCandidateInfo.class, result);
      assertTrue(result.isValidResult());
    });
  }

  @Test
  public void testProtectedM() {
    JavaResolveResult result = advancedResolve();
    ReadAction.runBlocking(() -> {
      assertTrue(result.getElement() instanceof PsiMethod);
      assertFalse(result.isAccessible());
    });
  }

  @Test
  public void testImplementOrder() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      PsiMethod method = (PsiMethod) target;
      assertEquals("II", method.getContainingClass().getName());
    });
  }

  @Test
  public void testObjectVsInterface() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiMethod.class, target);
      PsiMethod method = (PsiMethod) target;
      assertEquals("PublicCloneable", method.getContainingClass().getName());
    });
  }

  @Test
  public void testSwitchExpressionType() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> {
      PsiMethod target = (PsiMethod)resolve();
      assertEquals(PsiTypes.intType(), target.getParameterList().getParameters()[0].getType());
    });
  }

  @Test
  public void testMultipleJavadocReference() {
    PsiJavaReference ref = (PsiJavaReference)resolveFixture.get().findReferenceAtCaret("method/" + resolveFixture.get().getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      assertThat(ref.multiResolve(false)).hasSize(3);
    });
  }

  @Test
  public void testMultipleInheritancePathsToMethod() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    PsiReference ref = resolveFixture.get().findReferenceAtCaret("method/" + resolveFixture.get().getTestName(false) + ".java");

    // just assume this is called by some highlighting inspection/intention/pass before the resolve
    ReadAction.runBlocking(() -> {
      myFixture.findClass("NN").getAllMethods();

      PsiElement target = ref.resolve();
      assertInstanceOf(PsiMethod.class, target);
    });
  }
}
