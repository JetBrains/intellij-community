// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class ChangeSignaturePropagationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testParamSimple() {
    parameterPropagationTest();
  }

  @Test
  public void testParamWithOverriding() {
    parameterPropagationTest();
  }

  @Test
  public void testParamWithLambda() {
    parameterPropagationTest();
  }

  @Test
  public void testParamTypeSubst() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      final PsiMethod method = getPrimaryMethod();
      final HashSet<PsiMethod> methods = new HashSet<>();
      for (PsiReference reference : ReferencesSearch.search(method).asIterable()) {
        final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethod.class);
        if (psiMethod != null) {
          methods.add(psiMethod);
        }
      }
      parameterPropagationTest(method, methods, JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeByFQClassName("T"));
    });
  }

  @Test
  public void testConflictingParameterName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      final PsiMethod method = getPrimaryMethod();
      final HashSet<PsiMethod> methods = new HashSet<>();
      for (PsiReference reference : ReferencesSearch.search(method).asIterable()) {
        final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethod.class);
        if (psiMethod != null) {
          methods.add(psiMethod);
        }
      }
      PsiClassType stringType = PsiType.getJavaLangString(myFixture.getPsiManager(), GlobalSearchScope.allScope(myFixture.getProject()));
      final ParameterInfoImpl[] newParameters = {ParameterInfoImpl.createNew().withName("param").withType(stringType)};
      BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(newParameters, new ThrownExceptionInfo[0], methods, null, method));
    });
  }

  @Test
  public void testExceptionSimple() {
    exceptionPropagationTest();
  }

  @Test
  public void testExceptionWithOverriding() {
    exceptionPropagationTest();
  }

  @Test
  public void testParamWithNoConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      final PsiMethod method = getPrimaryMethod();
      parameterPropagationTest(method, collectNonPhysicalMethodsToPropagate(method), JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(myFixture.getProject())));
    });
  }

   @Test
   public void testExceptionWithNoConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      final PsiMethod method = getPrimaryMethod();
       exceptionPropagationTest(method, collectNonPhysicalMethodsToPropagate(method));
    });
  }

  private HashSet<PsiMethod> collectNonPhysicalMethodsToPropagate(PsiMethod method) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final HashSet<PsiMethod> methodsToPropagate = new HashSet<>();
    final PsiReference[] references =
      MethodReferencesSearch.search(method, GlobalSearchScope.allScope(myFixture.getProject()), true).toArray(PsiReference.EMPTY_ARRAY);
    for (PsiReference reference : references) {
      final PsiElement element = reference.getElement();
      assertInstanceOf(PsiClass.class, element);
      PsiClass containingClass = (PsiClass)element;
      methodsToPropagate.add(JavaPsiFacade.getElementFactory(myFixture.getProject()).createMethodFromText(containingClass.getName() + "(){}", containingClass));
    }
    return methodsToPropagate;
  }

  @Test
  public void testParamWithImplicitConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      final PsiMethod method = getPrimaryMethod();
      parameterPropagationTest(method, collectDefaultConstructorsToPropagate(method), JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(myFixture.getProject())));
    });
  }

  @Test
  public void testParamWithImplicitConstructors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      final PsiMethod method = getPrimaryMethod();
      parameterPropagationTest(method, collectDefaultConstructorsToPropagate(method), JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(myFixture.getProject())));
    });
  }

  @Test
  public void testExceptionWithImplicitConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      final PsiMethod method = getPrimaryMethod();
      exceptionPropagationTest(method, collectDefaultConstructorsToPropagate(method));
    });
  }

  private static HashSet<PsiMethod> collectDefaultConstructorsToPropagate(PsiMethod method) {
    final HashSet<PsiMethod> methodsToPropagate = new HashSet<>();
    for (PsiClass inheritor : ClassInheritorsSearch.search(method.getContainingClass()).asIterable()) {
      methodsToPropagate.add(inheritor.getConstructors()[0]);
    }
    return methodsToPropagate;
  }

  private void parameterPropagationTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      parameterPropagationTest(JavaPsiFacade.getElementFactory(myFixture.getProject())
                                 .createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(myFixture.getProject())));
    });
  }

  private void parameterPropagationTest(final PsiClassType paramType) {
    final PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, new HashSet<>(Arrays.asList(method.getContainingClass().getMethods())),
                             paramType);
  }

  private void parameterPropagationTest(final PsiMethod method, final HashSet<PsiMethod> psiMethods, final PsiType paramType) {
    final ParameterInfoImpl[] newParameters = new ParameterInfoImpl[]{ParameterInfoImpl.createNew().withName("clazz").withType(paramType).withDefaultValue("null")};
    doTest(newParameters, new ThrownExceptionInfo[0], psiMethods, null, method);
  }

  private void exceptionPropagationTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      final PsiMethod method = getPrimaryMethod();
      exceptionPropagationTest(method, new HashSet<>(Arrays.asList(method.getContainingClass().getMethods())));
    });
  }

  private void exceptionPropagationTest(final PsiMethod method, final Set<PsiMethod> methodsToPropagateExceptions) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClassType newExceptionType = JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeByFQClassName("java.lang.Exception", GlobalSearchScope.allScope(myFixture.getProject()));
    final ThrownExceptionInfo[] newExceptions = new ThrownExceptionInfo[]{new JavaThrownExceptionInfo(-1, newExceptionType)};
    doTest(new ParameterInfoImpl[0], newExceptions, null, methodsToPropagateExceptions, method);
  }

  private void doTest(ParameterInfoImpl[] newParameters,
                      final ThrownExceptionInfo[] newExceptions,
                      Set<PsiMethod> methodsToPropagateParameterChanges,
                      Set<PsiMethod> methodsToPropagateExceptionChanges,
                      PsiMethod primaryMethod) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String filePath = getBasePath() + myFixture.getTestName(false) + ".java";
    final PsiType returnType = primaryMethod.getReturnType();
    final CanonicalTypes.Type type = returnType == null ? null : CanonicalTypes.createTypeWrapper(returnType);
    new ChangeSignatureProcessor(myFixture.getProject(), primaryMethod, false, null,
                                 primaryMethod.getName(),
                                 type,
                                 generateParameterInfos(primaryMethod, newParameters),
                                 generateExceptionInfos(primaryMethod, newExceptions),
                                 methodsToPropagateParameterChanges,
                                 methodsToPropagateExceptionChanges).run();
    myFixture.checkResultByFile(filePath + ".after");
  }

  private PsiMethod getPrimaryMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String filePath = getBasePath() + myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertInstanceOf(PsiMethod.class, targetElement, "<caret> is not on method name");
    return (PsiMethod) targetElement;
  }

  private static String getBasePath() {
    return "/refactoring/changeSignaturePropagation/";
  }

  private static ParameterInfoImpl[] generateParameterInfos (PsiMethod method, ParameterInfoImpl[] newParameters) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    ParameterInfoImpl[] result = new ParameterInfoImpl[parameters.length + newParameters.length];
    for (int i = 0; i < parameters.length; i++) {
      result[i] = ParameterInfoImpl.create(i);
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

}
