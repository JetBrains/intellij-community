/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.properties.provider.PropertiesProvider;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

public final class TestUtils {
  public static final String RUN_WITH = "org.junit.runner.RunWith";
  private static final String PARAMETERIZED_FQN = "org.junit.runners.Parameterized";
  private static final CallMatcher ASSERT_THROWS =
    CallMatcher.staticCall(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertThrows");
  private static final String PER_CLASS_PROPERTY_KEY = "junit.jupiter.testinstance.lifecycle.default";

  private TestUtils() { }

  public static boolean isInTestSourceContent(@Nullable PsiElement element) {
    if (element == null) {
      return false;
    }
    final PsiFile file = element.getContainingFile();
    final VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
    return virtualFile != null && ProjectRootManager.getInstance(file.getProject()).getFileIndex().isInTestSourceContent(virtualFile);
  }

  public static boolean isPartOfJUnitTestMethod(@NotNull PsiElement element) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    return method != null && isJUnitTestMethod(method);
  }

  public static boolean isJUnitTestMethod(@Nullable PsiMethod method) {
    if (method == null) return false;
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    final Set<TestFramework> frameworks = TestFrameworks.detectApplicableFrameworks(containingClass);
    return ContainerUtil.exists(frameworks, framework -> framework.getName().startsWith("JUnit") && framework.isTestMethod(method, false));
  }

  public static boolean isRunnable(PsiMethod method) {
    if (method == null) {
      return false;
    }
    if (method.hasModifierProperty(PsiModifier.ABSTRACT) ||
        method.hasModifierProperty(PsiModifier.STATIC) ||
        !method.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }
    final PsiType returnType = method.getReturnType();
    if (!PsiTypes.voidType().equals(returnType)) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    return parameterList.isEmpty();
  }

  public static boolean isJUnit3TestMethod(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    final String methodName = method.getName();
    final @NonNls String test = "test";
    if (!methodName.startsWith(test) ||
        !method.hasModifierProperty(PsiModifier.PUBLIC) && !method.getParameterList().isEmpty()) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    return isJUnitTestClass(containingClass);
  }

  public static boolean isJUnit4TestMethod(@Nullable PsiMethod method) {
    return method != null && AnnotationUtil.isAnnotated(method, JUnitCommonClassNames.ORG_JUNIT_TEST, CHECK_HIERARCHY);
  }

  /**
   * @param frameworks to check matching with {@link TestFramework#getName}
   */
  public static boolean isExecutableTestMethod(@Nullable PsiMethod method, @Unmodifiable List<String> frameworks) {
    if (method == null) return false;
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    final TestFramework testFramework = TestFrameworks.detectFramework(containingClass);
    if (testFramework == null) return false;
    if (testFramework.isTestMethod(method, false)) {
      return ContainerUtil.exists(frameworks, testFramework.getName()::equals);
    }
    return false;
  }

  /**
   * @return whether a class is a JUnit 3 test class.
   */
  public static boolean isJUnitTestClass(@Nullable PsiClass targetClass) {
    return targetClass != null
           && InheritanceUtil.isInheritor(targetClass, JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE)
           && !AnnotationUtil.isAnnotated(targetClass, RUN_WITH, CHECK_HIERARCHY);
  }

  public static boolean isJUnit4TestClass(@Nullable PsiClass aClass, boolean runWithIsTestClass) {
    if (aClass == null) return false;
    if (AnnotationUtil.isAnnotated(aClass, RUN_WITH, CHECK_HIERARCHY)) return runWithIsTestClass;
    if (InheritanceUtil.isInheritor(aClass, JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE)) return false; // test will run with JUnit 3
    for (final PsiMethod method : aClass.getAllMethods()) {
      if (isExplicitlyJUnit4TestAnnotated(method)) return true;
    }
    return false;
  }

  public static boolean isInTestCode(PsiElement element) {
    if (isPartOfJUnitTestMethod(element)) {
      return true;
    }
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (containingClass != null && TestFrameworks.getInstance().isTestOrConfig(containingClass)) {
      return true;
    }
    return isInTestSourceContent(element);
  }

  /**
   * @return true if class is annotated with {@code @TestInstance(TestInstance.Lifecycle.PER_CLASS)}
   * or junit properties file has "per_class" property
   */
  public static boolean testInstancePerClass(@NotNull PsiClass containingClass) {
    return hasPerClassAnnotation(containingClass, new HashSet<>()) || hasPerClassProperty(containingClass);
  }

  private static boolean hasPerClassAnnotation(@NotNull PsiClass containingClass, HashSet<? super PsiClass> classes) {
    PsiAnnotation annotation = MetaAnnotationUtil.findMetaAnnotations(containingClass, Collections.singletonList(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_INSTANCE))
      .findFirst().orElse(null);
    if (annotation != null) {
      PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value != null && value.getText().contains("PER_CLASS")) {
        return true;
      }
    }
    else {
      for (PsiClass superClass : containingClass.getSupers()) {
        if (classes.add(superClass) && hasPerClassAnnotation(superClass, classes)) return true;
      }
    }
    return false;
  }

  private static boolean hasPerClassProperty(@NotNull PsiClass containingClass) {
    Module classModule = containingClass.isValid() ? ModuleUtilCore.findModuleForPsiElement(containingClass) : null;
    if (classModule == null) return false;
    final GlobalSearchScope globalSearchScope = GlobalSearchScope.moduleRuntimeScope(classModule, true);
    for (PropertiesProvider provider : PropertiesProvider.EP_NAME.getExtensionList()) {
      if ("PER_CLASS".equalsIgnoreCase(provider.getPropertyValue(PER_CLASS_PROPERTY_KEY, globalSearchScope))) return true;
    }
    return false;
  }

  /**
   * Tries to determine whether exception is expected at given element (e.g. element is a part of method annotated with
   * {@code @Test(expected = ...)} or part of lambda passed to {@code Assertions.assertThrows()}.
   * <p>
   * Note that the test is not exhaustive: false positives and false negatives are possible.
   *
   * @param element to check
   * @return true if it's likely that exception is expected at this point.
   */
  public static boolean isExceptionExpected(PsiElement element) {
    if (!isInTestSourceContent(element)) return false;
    for(; element != null && !(element instanceof PsiFile); element = element.getParent()) {
      if (element instanceof PsiMethod) {
        return hasExpectedExceptionAnnotation((PsiMethod)element);
      }
      if (element instanceof PsiLambdaExpression) {
        PsiExpressionList expressionList =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(element.getParent()), PsiExpressionList.class);
        if (expressionList != null) {
          PsiElement parent = expressionList.getParent();
          if (parent instanceof PsiMethodCallExpression && ASSERT_THROWS.test((PsiMethodCallExpression)parent)) return true;
        }
      }
      if (element instanceof PsiTryStatement && ((PsiTryStatement)element).getCatchBlocks().length > 0) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasExpectedExceptionAnnotation(PsiMethod method) {
    final PsiModifierList modifierList = method.getModifierList();
    return hasAnnotationWithParameter(modifierList, JUnitCommonClassNames.ORG_JUNIT_TEST, "expected") ||
           hasAnnotationWithParameter(modifierList, "org.testng.annotations.Test", "expectedExceptions");
  }

  private static boolean hasAnnotationWithParameter(PsiModifierList modifierList, String annotationName, String expectedParameterName) {
    final PsiAnnotation testAnnotation = modifierList.findAnnotation(annotationName);
    if (testAnnotation == null) {
      return false;
    }
    final PsiAnnotationParameterList parameterList = testAnnotation.getParameterList();
    final PsiNameValuePair[] nameValuePairs = parameterList.getAttributes();
    for (PsiNameValuePair nameValuePair : nameValuePairs) {
      final @NonNls String parameterName = nameValuePair.getName();
      if (expectedParameterName.equals(parameterName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @param method method to check
   * @return true if given method is directly annotated as JUnit4 test method
   */
  public static boolean isExplicitlyJUnit4TestAnnotated(@NotNull PsiMethod method) {
    return AnnotationUtil.isAnnotated(method, JUnitCommonClassNames.ORG_JUNIT_TEST, 0);
  }

  public static boolean isParameterizedTest(PsiClass aClass) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(aClass, RUN_WITH);
    if (annotation == null) {
      return false;
    }
    final PsiNameValuePair pair = AnnotationUtil.findDeclaredAttribute(annotation, null);
    if (pair == null) {
      return false;
    }
    final PsiAnnotationMemberValue value = pair.getValue();
    if (!(value instanceof PsiClassObjectAccessExpression)) {
      return false;
    }
    final PsiTypeElement typeElement = ((PsiClassObjectAccessExpression)value).getOperand();
    return typeElement.getType().getCanonicalText().equals(PARAMETERIZED_FQN);
  }
}
