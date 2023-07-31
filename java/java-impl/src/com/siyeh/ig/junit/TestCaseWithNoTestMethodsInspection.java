/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.ChangeModifierFix;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class TestCaseWithNoTestMethodsInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreSupers = true;

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "JUnitTestCaseWithNoTests";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "test.case.with.no.test.methods.problem.descriptor");
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    return (Boolean)infos[0] ? new ChangeModifierFix(PsiModifier.ABSTRACT) : null;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreSupers", InspectionGadgetsBundle.message(
        "test.case.with.no.test.methods.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TestCaseWithNoTestMethodsVisitor();
  }

  private class TestCaseWithNoTestMethodsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface()
          || aClass.isEnum()
          || aClass.isAnnotationType()
          || PsiUtil.isLocalOrAnonymousClass(aClass)
          || aClass.hasModifierProperty(PsiModifier.ABSTRACT)
          || AnnotationUtil.isAnnotated(aClass, JUnitCommonClassNames.ORG_JUNIT_IGNORE, 0)) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }

      Set<TestFramework> applicableFrameworks = TestFrameworks.detectApplicableFrameworks(aClass);
      if (applicableFrameworks.isEmpty()) {
        return;
      }

      Set<TestFramework> applicableToNestedClasses = applicableFrameworks.stream()
        .filter(framework -> framework instanceof JavaTestFramework && ((JavaTestFramework)framework).acceptNestedClasses())
        .collect(Collectors.toSet());
      if (hasTestMethods(aClass, applicableFrameworks, applicableToNestedClasses, true)) {
        return;
      }

      if (ignoreSupers) {
        PsiManager manager = aClass.getManager();
        PsiClass superClass = aClass.getSuperClass();
        while (superClass != null && manager.isInProject(superClass)) {
          if (hasTestMethods(superClass, applicableFrameworks, applicableToNestedClasses, false)) {
            return;
          }
          superClass = superClass.getSuperClass();
        }
      }
      registerClassError(aClass, ClassInheritorsSearch.search(aClass, aClass.getUseScope(), false).findFirst() != null);
    }

    private static boolean hasTestMethods(@NotNull PsiClass aClass,
                                          Set<TestFramework> selfFrameworks,
                                          Set<TestFramework> nestedTestFrameworks,
                                          boolean checkSuite) {

      PsiMethod[] methods = aClass.getMethods();

      for (TestFramework framework : selfFrameworks) {
        if (checkSuite && framework instanceof JavaTestFramework && ((JavaTestFramework)framework).isSuiteClass(aClass)) {
          return true;
        }

        if (ContainerUtil.exists(methods, method -> framework.isTestMethod(method, false))) {
          return true;
        }
      }

      if (!nestedTestFrameworks.isEmpty()) {
        for (PsiClass innerClass : aClass.getInnerClasses()) {
          if (!innerClass.hasModifierProperty(PsiModifier.STATIC) &&
              hasTestMethods(innerClass, nestedTestFrameworks, nestedTestFrameworks, false)) {
            return true;
          }
        }
      }

      return false;
    }
  }
}