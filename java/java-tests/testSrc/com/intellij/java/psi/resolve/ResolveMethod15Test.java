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
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

/**
 * @author dsl
 */
public class ResolveMethod15Test extends Resolve15TestCase {
  public void testStaticImportOnDemand() throws Exception {
    final PsiReference ref = configureByFile();
    final PsiElement element = ref.resolve();
    assertNotNull(element);
    assertThat(element, instanceOf(PsiMethod.class));
    final PsiMethod method = (PsiMethod)element;
    assertEquals("asList", method.getName());
    assertEquals("java.util.Arrays", method.getContainingClass().getQualifiedName());
  }

  public void testStaticImportHidden() throws Exception {
    final PsiJavaReference ref = (PsiJavaReference)configureByFile();
    final JavaResolveResult result = ref.advancedResolve(false);
    assertFalse(result.isValidResult());
    final PsiElement element = result.getElement();
    assertNotNull(element);
    assertThat(element, instanceOf(PsiMethod.class));
    final PsiMethod method = (PsiMethod)element;
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, method.getContainingClass().getQualifiedName());
  }

  public void testStaticImportDirect() throws Exception {
    final PsiReference ref = configureByFile();
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
  }


  public void testStaticImportConflict() throws Exception {
    final PsiReference ref = configureByFile();
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
  }

  public void testStaticImportConflict1() throws Exception {
    final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)configureByFile();
    final JavaResolveResult result = ref.advancedResolve(false);
    PsiElement element = result.getElement();
    assertTrue(!result.isValidResult());
    assertThat(element, instanceOf(PsiMethod.class));
    final PsiMethod method = (PsiMethod)element;
    PsiMethod parentMethod = PsiTreeUtil.getParentOfType(ref.getElement(), PsiMethod.class);
    assertEquals(method, parentMethod);
  }
  public void testStaticImportConflict3() throws Exception {
    final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)configureByFile();
    final JavaResolveResult result = ref.advancedResolve(false);
    assertResolvesToMethodInClass(result, "ToImportX2");
  }

  public void testGenericsAndVarargsNoConflict() throws Exception {
    final PsiReference ref = configureByFile();
    final PsiElement element = ref.resolve();
    assertNotNull(element);
    assertThat(element, instanceOf(PsiMethod.class));
    final PsiMethod method = (PsiMethod)element;
    assertEquals("method", method.getName());
    assertEquals(method.getTypeParameters().length, 0);
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    final JavaResolveResult[] resolveResults = refExpr.multiResolve(false);
    assertEquals(1, resolveResults.length);
    final JavaResolveResult resolveResult = resolveResults[0];
    assertTrue(resolveResult.isValidResult());
    assertThat(resolveResult, instanceOf(MethodCandidateInfo.class));
    final MethodCandidateInfo methodCandidateInfo = (MethodCandidateInfo)resolveResult;
    assertTrue(methodCandidateInfo.isApplicable());
  }

  //JLS3 15.2.8 hack
  public void testGetClass() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiType type = ((PsiExpression)refExpr.getParent()).getType();
    assertEquals("java.lang.Class<? extends java.lang.String>", type.getCanonicalText());
  }

  public void testToString() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    final PsiElement resolve = refExpr.resolve();
    assertTrue(resolve != null ? resolve.toString() : null, resolve instanceof PsiMethod);
    final PsiClass containingClass = ((PsiMethod)resolve).getContainingClass();
    assertTrue(containingClass != null ? containingClass.getName() : null, containingClass instanceof PsiAnonymousClass);
  }

  public void testListEquals() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    final PsiElement resolve = refExpr.resolve();
    assertTrue(resolve != null ? resolve.toString() : null, resolve instanceof PsiMethod);
    final PsiClass containingClass = ((PsiMethod)resolve).getContainingClass();
    assertNotNull(containingClass);
    assertTrue(containingClass.toString(), CommonClassNames.JAVA_UTIL_LIST.equals(containingClass.getQualifiedName()));
  }

  public void testCovariantReturnTypeAnonymous() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    final PsiElement resolve = refExpr.resolve();
    assertTrue(resolve != null ? resolve.toString() : null, resolve instanceof PsiMethod);
    final PsiClass containingClass = ((PsiMethod)resolve).getContainingClass();
    assertTrue(containingClass != null ? containingClass.getName() : null, !(containingClass instanceof PsiAnonymousClass));
  }

  public void testNonPublicAnonymous() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    final PsiElement resolve = refExpr.resolve();
    assertTrue(resolve != null ? resolve.toString() : null, resolve instanceof PsiMethod);
    final PsiClass containingClass = ((PsiMethod)resolve).getContainingClass();
    assertTrue(containingClass != null ? containingClass.getName() : null, !(containingClass instanceof PsiAnonymousClass));
  }

  public void testFilterFixedVsVarargs1() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
    assertNull(call.resolveMethod());
  }

  public void testFilterFixedVsVarargs2() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
    assertNull(call.resolveMethod());
  }

  public void testFilterFixedVsVarargs3() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
    assertNull(call.resolveMethod());
  }

  public void testFilterFixedVsVarargs4() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    assertNotNull(resolveResult.getElement());
    assertTrue(resolveResult.isValidResult());
  }

  public void testFilterFixedVsVarargs5() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    PsiElement element = resolveResult.getElement();
    assertNotNull(element);
    assertTrue(resolveResult.isValidResult());
    assertTrue(!((PsiMethod) element).isVarArgs());
  }

  public void testFilterFixedVsVarargs6() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    PsiElement element = resolveResult.getElement();
    assertNotNull(element);
    assertTrue(resolveResult.isValidResult());
    assertTrue(((PsiMethod) element).isVarArgs());
  }

  public void testFilterFixedVsVarargs7() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiMethodCallExpression call = (PsiMethodCallExpression) refExpr.getParent();
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    PsiElement element = resolveResult.getElement();
    assertNotNull(element);
    assertTrue(resolveResult.isValidResult());
    assertTrue(!((PsiMethod) element).isVarArgs());
  }

  public void testFilterFixedVsVarargs8() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    PsiElement element = resolveResult.getElement();
    assertNotNull(element);
    assertTrue(resolveResult.isValidResult());
    assertTrue(!((PsiMethod) element).isVarArgs());
  }
  public void testFilterFixedVsVarargs9() throws Exception {
    RecursionManager.assertOnRecursionPrevention(getTestRootDisposable());

    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    PsiElement element = resolveResult.getElement();
    assertNotNull(element);
    assertTrue(resolveResult.isValidResult());
    assertTrue(((PsiMethod) element).isVarArgs());
  }

  public void testFilterBoxing1() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    PsiElement element = resolveResult.getElement();
    assertNotNull(element);
    assertTrue(resolveResult.isValidResult());
    final PsiMethod method = (PsiMethod)element;
    assertEquals(PsiType.BOOLEAN, method.getParameterList().getParameters()[1].getType());
  }

  public void testFilterVarargsVsVarargs1() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    PsiElement element = resolveResult.getElement();
    assertNotNull(element);
    assertTrue(resolveResult.isValidResult());
    assertEquals(((PsiMethod)element).getParameterList().getParametersCount(), 3);
  }

  public void testFilterVarargsVsVarargs2() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    assertNull(resolveResult.getElement());
    assertFalse(resolveResult.isValidResult());

    final JavaResolveResult[] candidates = refExpr.multiResolve(false);
    assertEquals(2, candidates.length);
  }

  public void testFilterVarargsVsVarargs3() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    assertNotNull(resolveResult.getElement());
    assertFalse(resolveResult.isValidResult());

    final JavaResolveResult[] candidates = refExpr.multiResolve(false);
    assertEquals(1, candidates.length);
  }

  public void testFilterVarargsVsVarargs4() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    assertNull(resolveResult.getElement());
    assertFalse(resolveResult.isValidResult());

    final JavaResolveResult[] candidates = refExpr.multiResolve(false);
    assertEquals(2, candidates.length);
  }

  //IDEADEV-3313
  public void testCovariantReturnTypes() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    final PsiElement parent = refExpr.getParent();
    assertThat(parent, instanceOf(PsiMethodCallExpression.class));
    final PsiMethod method = ((PsiCall)parent).resolveMethod();
    assertNotNull(method);
    assertEquals("E", method.getContainingClass().getName());
  }

  public void testGenericMethods1() throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    final PsiElement parent = refExpr.getParent();
    assertThat(parent, instanceOf(PsiMethodCallExpression.class));
    final PsiMethodCallExpression expression = (PsiMethodCallExpression)parent;
    assertNull(expression.resolveMethod());
    final JavaResolveResult[] results = expression.getMethodExpression().multiResolve(false);
    assertEquals(2, results.length);
  }

  public void testGenericMethods2() throws Exception {
    final PsiReference ref = configureByFile();
    final PsiMethod method = checkResolvesUnique(ref);
    assertEquals(0, method.getTypeParameters().length);
  }

  public void testGenericMethods3() throws Exception {
    final PsiReference ref = configureByFile();
    final PsiMethod method = checkResolvesUnique(ref);
    assertEquals(0, method.getTypeParameters().length);
  }

  public void testGenericMethods4() throws Exception {
    final PsiReference ref = configureByFile();
    final PsiMethod method = checkResolvesUnique(ref);
    assertEquals(0, method.getTypeParameters().length);
  }

  public void testGenericMethods5() throws Exception {
    final PsiReference ref = configureByFile();
    final PsiMethod method = checkResolvesUnique(ref);
    assertEquals(2, method.getTypeParameters().length);
  }

  public void testGenericMethods6() throws Exception {
    final PsiReference ref = configureByFile();
    checkResolvesUnique(ref);
  }

  public void testGenericClass1() throws Exception {
    final PsiReference ref = configureByFile();
    final PsiMethod method = checkResolvesUnique(ref);
    assertEquals("Foo", method.getContainingClass().getName());
  }

  public void testGenericClass2() throws Exception {
    final PsiReference ref = configureByFile();
    final PsiMethod method = checkResolvesUnique(ref);
    assertEquals(0, method.getTypeParameters().length);
  }

  public void testMoreSpecificSameErasure() throws Exception {
    final PsiReference ref = configureByFile();
    final PsiMethod method = checkResolvesUnique(ref);
    assertEquals(0, method.getTypeParameters().length);
  }

  private PsiReference configureByFile() throws Exception {
    return configureByFile("method/generics/" + getTestName(false) + ".java");
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

  public void testTestGenericMethodOverloading1() throws Exception{
    PsiReference ref = configureByFile();
    PsiElement target = ref.resolve();
    assertThat(target, instanceOf(PsiMethod.class));
    assertThat(target.getParent(), instanceOf(PsiClass.class));
    assertEquals("Object", ((NavigationItem)target.getParent()).getName());
  }

  public void testPreferArrayTypeToObject() throws Exception {
    PsiReference ref = configureByFile();
    PsiElement target = ref.resolve();
    assertThat(target, instanceOf(PsiMethod.class));
    assertThat(target.getParent(), instanceOf(PsiClass.class));
    final PsiParameter[] parameters = ((PsiMethod)target).getParameterList().getParameters();
    assertEquals(1, parameters.length);
    assertTrue(parameters[0].getType() instanceof PsiArrayType);
  }

  public void testTestGenericMethodOverloading2() throws Exception{
    PsiReference ref = configureByFile();
    Collection<PsiElement> candidates = TargetElementUtil.getInstance().getTargetCandidates(ref);
    assertOneElement(candidates);
    PsiElement target = candidates.iterator().next();
    assertThat(target, instanceOf(PsiMethod.class));
    assertThat(target.getParent(), instanceOf(PsiClass.class));
    assertEquals("A", ((NavigationItem)target.getParent()).getName());
  }

  public void testTestGenericMethodOverloading3() throws Exception{
    PsiReference ref = configureByFile();
    PsiElement target = ref.resolve();
    assertThat(target, instanceOf(PsiMethod.class));
    assertThat(target.getParent(), instanceOf(PsiClass.class));
    assertEquals("Object", ((NavigationItem)target.getParent()).getName());
  }

  public void testTestGenericMethodOverloading4() throws Exception{
    PsiReference ref = configureByFile();
    PsiElement target = ref.resolve();
    assertThat(target, instanceOf(PsiMethod.class));
    assertThat(target.getParent(), instanceOf(PsiClass.class));
    assertEquals("A", ((NavigationItem)target.getParent()).getName());
  }

  public void testTestReturnType1() throws Exception{
    PsiReference ref = configureByFile();
    PsiElement target = ref.resolve();
    assertThat(target, instanceOf(PsiMethod.class));
  }

  public void testTestReturnType2() throws Exception{
    PsiReference ref = configureByFile();
    PsiElement target = ref.resolve();
    assertNull(target);
  }

  public void testMerge1() throws Exception{
    PsiReference ref = configureByFile();
    PsiElement target = ref.resolve();
    assertNull(target);
  }
  public void testExtends1() throws Exception{
    PsiReference ref = configureByFile();
    PsiElement target = ref.resolve();
    assertThat(target, instanceOf(PsiMethod.class));
  }
  public void testInheritance1() throws Exception{
    PsiReference ref = configureByFile();
    PsiElement target = ref.resolve();
    assertThat(target, instanceOf(PsiMethod.class));
  }

  public void testInheritance2() throws Exception{
    PsiReference ref = configureByFile();
    PsiElement target = ref.resolve();
    assertThat(target, instanceOf(PsiMethod.class));
  }

  public void testInheritance3() throws Exception{
    PsiReference ref = configureByFile();
    PsiElement target = ref.resolve();
    assertThat(target, instanceOf(PsiMethod.class));
  }

  public void testInheritance4() throws Exception{
    RecursionManager.assertOnRecursionPrevention(getTestRootDisposable());

    PsiReference ref = configureByFile();
    PsiElement target = ref.resolve();
    assertThat(target, instanceOf(PsiMethod.class));
  }

  public void testExplicitParams1() throws Exception {
    PsiReference ref = configureByFile();
    assertGenericResolve(ref, "f", new String[] {"java.lang.String"}, "java.lang.String");
  }

  public void testExplicitParams2() throws Exception {
    PsiReference ref = configureByFile();
    assertGenericResolve(ref, "f", new String[] {"java.lang.Integer"}, "Foo");
  }

  public void testConstructorExplicitParams() throws Exception {
    PsiReference ref = configureByFile();
    assertThat(ref.getElement(), instanceOf(PsiJavaCodeReferenceElement.class));
    assertThat(ref.getElement().getParent(), instanceOf(PsiNewExpression.class));
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

  public void testRawMethod1() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    PsiElement target = ref.resolve();
    assertThat(target, instanceOf(PsiMethod.class));
  }
  public void testDependingParams2() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    final JavaResolveResult result = ref.advancedResolve(true);
    assertTrue(result.isValidResult());
  }

  public void testTypeInference1() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    final JavaResolveResult result = ref.advancedResolve(true);
    assertNotNull(result.getElement());
  }


  public void testRawVsGenericConflict() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    final JavaResolveResult result = ref.advancedResolve(true);
    assertResolvesToMethodInClass(result, "A");
  }

  public void testRawInheritanceConflict() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    final JavaResolveResult[] result = ref.multiResolve(false);
    assertEquals("False ambiguity", 1, result.length);
  }

  public void testRawVsGenericConflictInCaseOfOverride() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    final JavaResolveResult result = ref.advancedResolve(true);
    assertResolvesToMethodInClass(result, "B");
  }

  public void testRawVsGenericConflictInCaseOfOverride2() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    final JavaResolveResult result = ref.advancedResolve(true);
    assertResolvesToMethodInClass(result, "TestProcessor");
  }

  public void testAutoboxingAndWidening() throws Exception{
    PsiJavaReference ref = (PsiJavaReference) configureByFile();
    final JavaResolveResult result = ref.advancedResolve(true);
    assertNotNull(result.getElement());
    assertTrue(result.isValidResult());
  }

  public void testSOE() throws Exception {
    PsiReference ref = configureByFile();
    ref.resolve();
  }
  public void testHidingSuperPrivate() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    final JavaResolveResult result = ref.advancedResolve(true);
    assertResolvesToMethodInClass(result, "S");
  }
  public void testNestedTypeParams() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    final JavaResolveResult result = ref.advancedResolve(true);
    assertResolvesToMethodInClass(result, "TestImpl");
  }
  public void testTypeParamBoundConflict() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    final JavaResolveResult result = ref.advancedResolve(true);
    assertResolvesToMethodInClass(result, "Testergen");
  }
  public void testAmbiguousBoxing() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    final JavaResolveResult result = ref.advancedResolve(true);
    assertFalse(result.isValidResult());

    JavaResolveResult[] results = ref.multiResolve(false);
    assertEquals(2, results.length);
    assertEquals("f", ((PsiMethod)results[0].getElement()).getName());
    assertEquals("f", ((PsiMethod)results[1].getElement()).getName());
  }
  
  public void testStaticMethodInSubclass() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile();
    final JavaResolveResult result = ref.advancedResolve(true);
    assertNull(result.getElement());
  }

  private static void assertResolvesToMethodInClass(JavaResolveResult result, @NonNls String name) {
    PsiMethod method = (PsiMethod)result.getElement();
    assertNotNull(method);
    assertTrue(result.isValidResult());
    assertEquals(name, method.getContainingClass().getName());
  }
}
