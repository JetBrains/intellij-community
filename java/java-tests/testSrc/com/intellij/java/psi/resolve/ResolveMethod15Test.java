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

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightResolveFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static com.intellij.testFramework.LightResolveFixtureKt.lightResolveFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveMethod15Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_5);
  private final TestFixture<LightResolveFixture> resolveFixture = lightResolveFixture(projectFixture);

  @Test
  public void testStaticImportOnDemand() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      final PsiElement element = ref.resolve();
      assertNotNull(element);
      assertThat(element, instanceOf(PsiMethod.class));
      final PsiMethod method = (PsiMethod)element;
      assertEquals("asList", method.getName());
      assertEquals("java.util.Arrays", method.getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testStaticImportHidden() throws Exception {
    final PsiJavaReference ref = (PsiJavaReference)configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(false);
      assertFalse(result.isValidResult());
      final PsiElement element = result.getElement();
      assertNotNull(element);
      assertThat(element, instanceOf(PsiMethod.class));
      final PsiMethod method = (PsiMethod)element;
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, method.getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testStaticImportDirect() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      final PsiElement element = ref.resolve();
      assertNotNull(element);
      assertThat(element, instanceOf(PsiMethod.class));
      final PsiMethod method = (PsiMethod)element;
      assertEquals("asList", method.getName());
      assertEquals("java.util.Arrays", method.getContainingClass().getQualifiedName());
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      final JavaResolveResult[] resolveResults = refExpr.multiResolve(false);
      assertEquals(1, resolveResults.length);
      final JavaResolveResult resolveResult = resolveResults[0];
      assertTrue(resolveResult.isValidResult());
      assertThat(resolveResult.getCurrentFileResolveScope(), instanceOf(PsiImportStaticStatement.class));
      assertThat(resolveResult, instanceOf(MethodCandidateInfo.class));
      final MethodCandidateInfo methodCandidateInfo = (MethodCandidateInfo)resolveResult;
      assertTrue(methodCandidateInfo.isApplicable());
    });
  }

  @Test
  public void testStaticImportConflict() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      final PsiElement element = ref.resolve();
      assertNotNull(element);
      assertThat(element, instanceOf(PsiMethod.class));
      final PsiMethod method = (PsiMethod)element;
      assertEquals("sort", method.getName());
      assertEquals("java.util.Collections", method.getContainingClass().getQualifiedName());
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      final JavaResolveResult[] resolveResults = refExpr.multiResolve(false);
      assertEquals(1, resolveResults.length);
      final JavaResolveResult resolveResult = resolveResults[0];
      assertFalse(resolveResult.isValidResult());
      assertThat(resolveResult.getCurrentFileResolveScope(), instanceOf(PsiImportStaticStatement.class));
      assertThat(resolveResult, instanceOf(MethodCandidateInfo.class));
      final MethodCandidateInfo methodCandidateInfo = (MethodCandidateInfo)resolveResult;
      assertFalse(methodCandidateInfo.isApplicable());
    });
  }

  @Test
  public void testStaticImportConflict1() throws Exception {
    final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(false);
      PsiElement element = result.getElement();
      assertFalse(result.isValidResult());
      assertThat(element, instanceOf(PsiMethod.class));
      final PsiMethod method = (PsiMethod)element;
      PsiMethod parentMethod = PsiTreeUtil.getParentOfType(ref.getElement(), PsiMethod.class);
      assertEquals(method, parentMethod);
    });
  }
  @Test
  public void testStaticImportConflict3() throws Exception {
    final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(false);
      assertResolvesToMethodInClass(result, "ToImportX2");
    });
  }

  @Test
  public void testGenericsAndVarargsNoConflict() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      final PsiElement element = ref.resolve();
      assertNotNull(element);
      assertThat(element, instanceOf(PsiMethod.class));
      final PsiMethod method = (PsiMethod)element;
      assertEquals("method", method.getName());
      assertEquals(0, method.getTypeParameters().length);
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      final JavaResolveResult[] resolveResults = refExpr.multiResolve(false);
      assertEquals(1, resolveResults.length);
      final JavaResolveResult resolveResult = resolveResults[0];
      assertTrue(resolveResult.isValidResult());
      assertThat(resolveResult, instanceOf(MethodCandidateInfo.class));
      final MethodCandidateInfo methodCandidateInfo = (MethodCandidateInfo)resolveResult;
      assertTrue(methodCandidateInfo.isApplicable());
    });
  }

  //JLS3 15.2.8 hack
  @Test
  public void testGetClass() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiType type = ((PsiExpression)refExpr.getParent()).getType();
      assertEquals("java.lang.Class<? extends java.lang.String>", type.getCanonicalText());
    });
  }

  @Test
  public void testToString() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      final PsiElement resolve = refExpr.resolve();
      assertTrue(resolve instanceof PsiMethod, resolve != null ? resolve.toString() : null);
      final PsiClass containingClass = ((PsiMethod)resolve).getContainingClass();
      assertNotNull(containingClass);
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, containingClass.getQualifiedName());
    });
  }

  @Test
  public void testListEquals() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      final PsiElement resolve = refExpr.resolve();
      assertTrue(resolve instanceof PsiMethod, resolve != null ? resolve.toString() : null);
      final PsiClass containingClass = ((PsiMethod)resolve).getContainingClass();
      assertNotNull(containingClass);
      assertEquals(CommonClassNames.JAVA_UTIL_LIST, containingClass.getQualifiedName(), containingClass.toString());
    });
  }

  @Test
  public void testCovariantReturnTypeAnonymous() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      final PsiElement resolve = refExpr.resolve();
      assertTrue(resolve instanceof PsiMethod, resolve != null ? resolve.toString() : null);
      final PsiClass containingClass = ((PsiMethod)resolve).getContainingClass();
      assertFalse(containingClass instanceof PsiAnonymousClass, containingClass != null ? containingClass.getName() : null);
    });
  }

  @Test
  public void testNonPublicAnonymous() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      final PsiElement resolve = refExpr.resolve();
      assertTrue(resolve instanceof PsiMethod, resolve != null ? resolve.toString() : null);
      final PsiClass containingClass = ((PsiMethod)resolve).getContainingClass();
      assertFalse(containingClass instanceof PsiAnonymousClass, containingClass != null ? containingClass.getName() : null);
    });
  }

  @Test
  public void testFilterFixedVsVarargs1() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
      assertNull(call.resolveMethod());
    });
  }

  @Test
  public void testFilterFixedVsVarargs2() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
      assertNull(call.resolveMethod());
    });
  }

  @Test
  public void testFilterFixedVsVarargs3() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
      assertNull(call.resolveMethod());
    });
  }

  @Test
  public void testFilterFixedVsVarargs4() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
      JavaResolveResult resolveResult = call.resolveMethodGenerics();
      assertNotNull(resolveResult.getElement());
      assertTrue(resolveResult.isValidResult());
    });
  }

  @Test
  public void testFilterFixedVsVarargs5() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
      JavaResolveResult resolveResult = call.resolveMethodGenerics();
      PsiElement element = resolveResult.getElement();
      assertNotNull(element);
      assertTrue(resolveResult.isValidResult());
      assertFalse(((PsiMethod)element).isVarArgs());
    });
  }

  @Test
  public void testFilterFixedVsVarargs6() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
      JavaResolveResult resolveResult = call.resolveMethodGenerics();
      PsiElement element = resolveResult.getElement();
      assertNotNull(element);
      assertTrue(resolveResult.isValidResult());
      assertTrue(((PsiMethod) element).isVarArgs());
    });
  }

  @Test
  public void testFilterFixedVsVarargs7() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
      JavaResolveResult resolveResult = call.resolveMethodGenerics();
      PsiElement element = resolveResult.getElement();
      assertNotNull(element);
      assertTrue(resolveResult.isValidResult());
      assertFalse(((PsiMethod)element).isVarArgs());
    });
  }

  @Test
  public void testFilterFixedVsVarargs8() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
      JavaResolveResult resolveResult = call.resolveMethodGenerics();
      PsiElement element = resolveResult.getElement();
      assertNotNull(element);
      assertTrue(resolveResult.isValidResult());
      assertFalse(((PsiMethod)element).isVarArgs());
    });
  }
  @Test
  public void testFilterFixedVsVarargs9() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());

    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
      JavaResolveResult resolveResult = call.resolveMethodGenerics();
      PsiElement element = resolveResult.getElement();
      assertNotNull(element);
      assertTrue(resolveResult.isValidResult());
      assertTrue(((PsiMethod) element).isVarArgs());
    });
  }

  @Test
  public void testFilterBoxing1() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
      JavaResolveResult resolveResult = call.resolveMethodGenerics();
      PsiElement element = resolveResult.getElement();
      assertNotNull(element);
      assertTrue(resolveResult.isValidResult());
      final PsiMethod method = (PsiMethod)element;
      assertEquals(PsiTypes.booleanType(), method.getParameterList().getParameters()[1].getType());
    });
  }

  @Test
  public void testFilterVarargsVsVarargs1() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
      JavaResolveResult resolveResult = call.resolveMethodGenerics();
      PsiElement element = resolveResult.getElement();
      assertNotNull(element);
      assertTrue(resolveResult.isValidResult());
      assertEquals(3, ((PsiMethod)element).getParameterList().getParametersCount());
    });
  }

  @Test
  public void testFilterVarargsVsVarargs2() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
      JavaResolveResult resolveResult = call.resolveMethodGenerics();
      assertNull(resolveResult.getElement());
      assertFalse(resolveResult.isValidResult());

      final JavaResolveResult[] candidates = refExpr.multiResolve(false);
      assertEquals(2, candidates.length);
    });
  }

  @Test
  public void testFilterVarargsVsVarargs3() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
      JavaResolveResult resolveResult = call.resolveMethodGenerics();
      assertNotNull(resolveResult.getElement());
      assertFalse(resolveResult.isValidResult());

      final JavaResolveResult[] candidates = refExpr.multiResolve(false);
      assertEquals(1, candidates.length);
    });
  }

  @Test
  public void testFilterVarargsVsVarargs4() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
      JavaResolveResult resolveResult = call.resolveMethodGenerics();
      assertNull(resolveResult.getElement());
      assertFalse(resolveResult.isValidResult());

      final JavaResolveResult[] candidates = refExpr.multiResolve(false);
      assertEquals(2, candidates.length);
    });
  }

  //IDEADEV-3313
  @Test
  public void testCovariantReturnTypes() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      final PsiElement parent = refExpr.getParent();
      assertThat(parent, instanceOf(PsiMethodCallExpression.class));
      final PsiMethod method = ((PsiCall)parent).resolveMethod();
      assertNotNull(method);
      assertEquals("E", method.getContainingClass().getName());
    });
  }

  @Test
  public void testGenericMethods1() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref, instanceOf(PsiReferenceExpression.class));
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      final PsiElement parent = refExpr.getParent();
      assertThat(parent, instanceOf(PsiMethodCallExpression.class));
      final PsiMethodCallExpression expression = (PsiMethodCallExpression)parent;
      assertNull(expression.resolveMethod());
      final JavaResolveResult[] results = expression.multiResolve(false);
      assertEquals(2, results.length);
    });
  }

  @Test
  public void testGenericMethods2() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      final PsiMethod method = checkResolvesUnique(ref);
      assertEquals(0, method.getTypeParameters().length);
    });
  }

  @Test
  public void testGenericMethods3() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      final PsiMethod method = checkResolvesUnique(ref);
      assertEquals(0, method.getTypeParameters().length);
    });
  }

  @Test
  public void testGenericMethods4() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      final PsiMethod method = checkResolvesUnique(ref);
      assertEquals(0, method.getTypeParameters().length);
    });
  }

  @Test
  public void testGenericMethods5() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      final PsiMethod method = checkResolvesUnique(ref);
      assertEquals(2, method.getTypeParameters().length);
    });
  }

  @Test
  public void testGenericMethods6() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      checkResolvesUnique(ref);
    });
  }

  @Test
  public void testGenericClass1() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      final PsiMethod method = checkResolvesUnique(ref);
      assertEquals("Foo", method.getContainingClass().getName());
    });
  }

  @Test
  public void testGenericClass2() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      final PsiMethod method = checkResolvesUnique(ref);
      assertEquals(0, method.getTypeParameters().length);
    });
  }

  @Test
  public void testMoreSpecificSameErasure() throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      final PsiMethod method = checkResolvesUnique(ref);
      assertEquals(0, method.getTypeParameters().length);
    });
  }

  private PsiReference configureByFile() throws Exception {
    return resolveFixture.get().findReferenceAtCaret("method/generics/" + resolveFixture.get().getTestName(false) + ".java");
  }
  private static PsiMethod checkResolvesUnique(final PsiReference ref) {
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    final PsiElement parent = refExpr.getParent();
    assertThat(parent, instanceOf(PsiMethodCallExpression.class));
    final PsiMethodCallExpression expression = (PsiMethodCallExpression)parent;
    final PsiMethod method = expression.resolveMethod();
    assertNotNull(method);
    return method;
  }

  @Test
  public void testTestGenericMethodOverloading1() throws Exception{
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertThat(target, instanceOf(PsiMethod.class));
      assertThat(target.getParent(), instanceOf(PsiClass.class));
      assertEquals("Object", ((NavigationItem)target.getParent()).getName());
    });
  }

  @Test
  public void testPreferArrayTypeToObject() throws Exception {
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertThat(target, instanceOf(PsiMethod.class));
      assertThat(target.getParent(), instanceOf(PsiClass.class));
      final PsiParameter[] parameters = ((PsiMethod)target).getParameterList().getParameters();
      assertEquals(1, parameters.length);
      assertTrue(parameters[0].getType() instanceof PsiArrayType);
    });
  }

  @Test
  public void testTestGenericMethodOverloading2() throws Exception{
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      Collection<PsiElement> candidates = TargetElementUtil.getInstance().getTargetCandidates(ref);
      Assertions.assertThat(candidates).hasSize(1);
      PsiElement target = candidates.iterator().next();
      assertThat(target, instanceOf(PsiMethod.class));
      assertThat(target.getParent(), instanceOf(PsiClass.class));
      assertEquals("A", ((NavigationItem)target.getParent()).getName());
    });
  }

  @Test
  public void testTestGenericMethodOverloading3() throws Exception{
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertThat(target, instanceOf(PsiMethod.class));
      assertThat(target.getParent(), instanceOf(PsiClass.class));
      assertEquals("Object", ((NavigationItem)target.getParent()).getName());
    });
  }

  @Test
  public void testTestGenericMethodOverloading4() throws Exception{
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertThat(target, instanceOf(PsiMethod.class));
      assertThat(target.getParent(), instanceOf(PsiClass.class));
      assertEquals("A", ((NavigationItem)target.getParent()).getName());
    });
  }

  @Test
  public void testTestReturnType1() throws Exception{
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertThat(target, instanceOf(PsiMethod.class));
    });
  }

  @Test
  public void testTestReturnType2() throws Exception{
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertNull(target);
    });
  }

  @Test
  public void testMerge1() throws Exception{
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertNull(target);
    });
  }
  @Test
  public void testExtends1() throws Exception{
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertThat(target, instanceOf(PsiMethod.class));
    });
  }
  @Test
  public void testInheritance1() throws Exception{
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertThat(target, instanceOf(PsiMethod.class));
    });
  }

  @Test
  public void testInheritance2() throws Exception{
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertThat(target, instanceOf(PsiMethod.class));
    });
  }

  @Test
  public void testInheritance3() throws Exception{
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertThat(target, instanceOf(PsiMethod.class));
    });
  }

  @Test
  public void testInheritance4() throws Exception{
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());

    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertThat(target, instanceOf(PsiMethod.class));
    });
  }

  @Test
  public void testExplicitParams1() throws Exception {
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertGenericResolve(ref, "f", new String[] {"java.lang.String"}, "java.lang.String");
    });
  }

  @Test
  public void testExplicitParams2() throws Exception {
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertGenericResolve(ref, "f", new String[] {"java.lang.Integer"}, "Foo");
    });
  }

  @Test
  public void testConstructorExplicitParams() throws Exception {
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertThat(ref.getElement(), instanceOf(PsiJavaCodeReferenceElement.class));
      assertThat(ref.getElement().getParent(), instanceOf(PsiNewExpression.class));
    });
  }

  private static void assertGenericResolve(PsiReference ref, final String methodName, final String[] expectedTypeParameterValues, @NonNls final String expectedCallType) {
    PsiElement target = ref.resolve();
    assertThat(target, instanceOf(PsiMethod.class));

    PsiMethod psiMethod = (PsiMethod)target;
    assertEquals(methodName, psiMethod.getName());
    assertThat(ref.getElement(), instanceOf(PsiJavaCodeReferenceElement.class));
    PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement)ref.getElement();
    JavaResolveResult resolveResult = refElement.advancedResolve(false);
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    PsiTypeParameter[] typeParameters = psiMethod.getTypeParameters();
    assertEquals(expectedTypeParameterValues.length, typeParameters.length);
    for (int i = 0; i < expectedTypeParameterValues.length; i++) {
      String expectedTypeParameterValue = expectedTypeParameterValues[i];
      assertTrue(substitutor.substitute(typeParameters[i]).equalsToText(expectedTypeParameterValue));
    }
    PsiType type = ((PsiExpression)refElement.getParent()).getType();
    assertTrue(type.equalsToText(expectedCallType));
  }

  @Test
  public void testRawMethod1() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    ReadAction.runBlocking(() -> {
      PsiElement target = ref.resolve();
      assertThat(target, instanceOf(PsiMethod.class));
    });
  }
  @Test
  public void testDependingParams2() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(true);
      assertTrue(result.isValidResult());
    });
  }

  @Test
  public void testTypeInference1() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(true);
      assertNotNull(result.getElement());
    });
  }

  @Test
  public void testRawVsGenericConflict() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(true);
      assertResolvesToMethodInClass(result, "A");
    });
  }

  @Test
  public void testRawInheritanceConflict() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult[] result = ref.multiResolve(false);
      assertEquals(1, result.length, "False ambiguity");
    });
  }

  @Test
  public void testRawVsGenericConflictInCaseOfOverride() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(true);
      assertResolvesToMethodInClass(result, "B");
    });
  }

  @Test
  public void testRawVsGenericConflictInCaseOfOverride2() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(true);
      assertResolvesToMethodInClass(result, "TestProcessor");
    });
  }

  @Test
  public void testAutoboxingAndWidening() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(true);
      assertNotNull(result.getElement());
      assertTrue(result.isValidResult());
    });
  }

  @Test
  public void testSOE() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    RecursionManager.disableMissedCacheAssertions(myFixture.getTestRootDisposable());
    PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
      assertNull(ref.resolve());
    });
  }
  @Test
  public void testHidingSuperPrivate() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(true);
      assertResolvesToMethodInClass(result, "S");
    });
  }
  @Test
  public void testNestedTypeParams() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(true);
      assertResolvesToMethodInClass(result, "TestImpl");
    });
  }
  @Test
  public void testTypeParamBoundConflict() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(true);
      assertResolvesToMethodInClass(result, "Testergen");
    });
  }
  @Test
  public void testAmbiguousBoxing() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(true);
      assertFalse(result.isValidResult());

      JavaResolveResult[] results = ref.multiResolve(false);
      assertEquals(2, results.length);
      assertEquals("f", ((PsiMethod)results[0].getElement()).getName());
      assertEquals("f", ((PsiMethod)results[1].getElement()).getName());
    });
  }
  
  @Test
  public void testStaticMethodInSubclass() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    ReadAction.runBlocking(() -> {
      final JavaResolveResult result = ref.advancedResolve(true);
      assertNull(result.getElement());
    });
  }

  @Test
  public void testStaticImportOnEnumValues() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    ReadAction.runBlocking(() -> {
      JavaResolveResult result = ref.advancedResolve(true);
      PsiMethod method = (PsiMethod)result.getElement();
      assertNotNull(method);
      PsiClass containingClass = method.getContainingClass();
      assertEquals("RetentionPolicy", containingClass.getName());
    });
  }

  private static void assertResolvesToMethodInClass(JavaResolveResult result, @NonNls String name) {
    PsiMethod method = (PsiMethod)result.getElement();
    assertNotNull(method);
    assertTrue(result.isValidResult());
    assertEquals(name, method.getContainingClass().getName());
  }
}
