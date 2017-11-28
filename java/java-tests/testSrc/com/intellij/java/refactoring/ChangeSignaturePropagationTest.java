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
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.containers.HashSet;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;

/**
 * @author ven
 */
public class ChangeSignaturePropagationTest extends LightRefactoringTestCase  {
  public void testParamSimple() {
    parameterPropagationTest();
  }

  public void testParamWithOverriding() {
    parameterPropagationTest();
  }

  public void testParamTypeSubst() {
    final PsiMethod method = getPrimaryMethod();
    final HashSet<PsiMethod> methods = new HashSet<>();
    for (PsiReference reference : ReferencesSearch.search(method)) {
      final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethod.class);
      if (psiMethod != null) {
        methods.add(psiMethod);
      }
    }
    parameterPropagationTest(method, methods, JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("T"));
  }

  public void testExceptionSimple() {
    exceptionPropagationTest();
  }

  public void testExceptionWithOverriding() {
    exceptionPropagationTest();
  }

  public void testParamWithNoConstructor() {
    final PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, collectNonPhysicalMethodsToPropagate(method), JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(getProject())));
  }

   public void testExceptionWithNoConstructor() {
    final PsiMethod method = getPrimaryMethod();
     exceptionPropagationTest(method, collectNonPhysicalMethodsToPropagate(method));
  }

  private static HashSet<PsiMethod> collectNonPhysicalMethodsToPropagate(PsiMethod method) {
    final HashSet<PsiMethod> methodsToPropagate = new HashSet<>();
    final PsiReference[] references =
      MethodReferencesSearch.search(method, GlobalSearchScope.allScope(getProject()), true).toArray(PsiReference.EMPTY_ARRAY);
    for (PsiReference reference : references) {
      final PsiElement element = reference.getElement();
      Assert.assertTrue(element instanceof PsiClass);
      PsiClass containingClass = (PsiClass)element;
      methodsToPropagate.add(JavaPsiFacade.getElementFactory(getProject()).createMethodFromText(containingClass.getName() + "(){}", containingClass));
    }
    return methodsToPropagate;
  }

  public void testParamWithImplicitConstructor() {
    final PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, collectDefaultConstructorsToPropagate(method), JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(getProject())));
  }

  public void testParamWithImplicitConstructors() {
    final PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, collectDefaultConstructorsToPropagate(method), JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(getProject())));
  }

  public void testExceptionWithImplicitConstructor() {
    final PsiMethod method = getPrimaryMethod();
    exceptionPropagationTest(method, collectDefaultConstructorsToPropagate(method));
  }

  private static HashSet<PsiMethod> collectDefaultConstructorsToPropagate(PsiMethod method) {
    final HashSet<PsiMethod> methodsToPropagate = new HashSet<>();
    for (PsiClass inheritor : ClassInheritorsSearch.search(method.getContainingClass())) {
      methodsToPropagate.add(inheritor.getConstructors()[0]);
    }
    return methodsToPropagate;
  }

  private void parameterPropagationTest() {
    parameterPropagationTest(JavaPsiFacade.getElementFactory(getProject())
                               .createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(getProject())));
  }

  private void parameterPropagationTest(final PsiClassType paramType) {
    final PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, new HashSet<>(Arrays.asList(method.getContainingClass().getMethods())),
                             paramType);
  }

  private void parameterPropagationTest(final PsiMethod method, final HashSet<PsiMethod> psiMethods, final PsiType paramType) {
    final ParameterInfoImpl[] newParameters = new ParameterInfoImpl[]{new ParameterInfoImpl(-1, "clazz", paramType, "null")};
    doTest(newParameters, new ThrownExceptionInfo[0], psiMethods, null, method);
  }

  private void exceptionPropagationTest() {
    final PsiMethod method = getPrimaryMethod();
    exceptionPropagationTest(method, new HashSet<>(Arrays.asList(method.getContainingClass().getMethods())));
  }

  private void exceptionPropagationTest(final PsiMethod method, final Set<PsiMethod> methodsToPropagateExceptions) {
    PsiClassType newExceptionType = JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("java.lang.Exception", GlobalSearchScope.allScope(getProject()));
    final ThrownExceptionInfo[] newExceptions = new ThrownExceptionInfo[]{new JavaThrownExceptionInfo(-1, newExceptionType)};
    doTest(new ParameterInfoImpl[0], newExceptions, null, methodsToPropagateExceptions, method);
  }

  private void doTest(ParameterInfoImpl[] newParameters,
                      final ThrownExceptionInfo[] newExceptions,
                      Set<PsiMethod> methodsToPropagateParameterChanges,
                      Set<PsiMethod> methodsToPropagateExceptionChanges,
                      PsiMethod primaryMethod) {
    final String filePath = getBasePath() + getTestName(false) + ".java";
    final PsiType returnType = primaryMethod.getReturnType();
    final CanonicalTypes.Type type = returnType == null ? null : CanonicalTypes.createTypeWrapper(returnType);
    new ChangeSignatureProcessor(getProject(), primaryMethod, false, null,
                                 primaryMethod.getName(),
                                 type,
                                 generateParameterInfos(primaryMethod, newParameters),
                                 generateExceptionInfos(primaryMethod, newExceptions),
                                 methodsToPropagateParameterChanges,
                                 methodsToPropagateExceptionChanges).run();
    checkResultByFile(filePath + ".after");
  }

  private PsiMethod getPrimaryMethod() {
    final String filePath = getBasePath() + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    return (PsiMethod) targetElement;
  }

  private static String getBasePath() {
    return "/refactoring/changeSignaturePropagation/";
  }

  private static ParameterInfoImpl[] generateParameterInfos (PsiMethod method, ParameterInfoImpl[] newParameters) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    ParameterInfoImpl[] result = new ParameterInfoImpl[parameters.length + newParameters.length];
    for (int i = 0; i < parameters.length; i++) {
      result[i] = new ParameterInfoImpl(i);
    }
    System.arraycopy(newParameters, 0, result, parameters.length, newParameters.length);
    return result;
  }

  private static ThrownExceptionInfo[] generateExceptionInfos (PsiMethod method, ThrownExceptionInfo[] newExceptions) {
    final PsiClassType[] exceptions = method.getThrowsList().getReferencedTypes();
    ThrownExceptionInfo[] result = new ThrownExceptionInfo[exceptions.length + newExceptions.length];
    for (int i = 0; i < exceptions.length; i++) {
      result[i] = new JavaThrownExceptionInfo(i);
    }
    System.arraycopy(newExceptions, 0, result, exceptions.length, newExceptions.length);
    return result;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

}
