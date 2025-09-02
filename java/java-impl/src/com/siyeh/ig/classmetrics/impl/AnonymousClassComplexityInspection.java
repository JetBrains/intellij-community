/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classmetrics.impl;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.classmetrics.ClassMetricInspection;
import com.siyeh.ig.classmetrics.CyclomaticComplexityVisitor;
import com.siyeh.ig.fixes.MoveAnonymousToInnerClassFix;
import org.jetbrains.annotations.NotNull;

public final class AnonymousClassComplexityInspection
  extends ClassMetricInspection {

  private static final int DEFAULT_COMPLEXITY_LIMIT = 3;

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new MoveAnonymousToInnerClassFix();
  }

  @Override
  public @NotNull String getID() {
    return "OverlyComplexAnonymousInnerClass";
  }

  @Override
  protected int getDefaultLimit() {
    return DEFAULT_COMPLEXITY_LIMIT;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message(
      "cyclomatic.complexity.limit.option");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    final Integer totalComplexity = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "overly.complex.anonymous.inner.class.problem.descriptor",
      totalComplexity);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassComplexityVisitor();
  }

  private class ClassComplexityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAnonymousClass(
      @NotNull PsiAnonymousClass aClass) {
      if (aClass instanceof PsiEnumConstantInitializer) {
        return;
      }
      final int totalComplexity = calculateTotalComplexity(aClass);
      if (totalComplexity <= getLimit()) {
        return;
      }
      registerClassError(aClass, Integer.valueOf(totalComplexity));
    }

    private static int calculateTotalComplexity(PsiClass aClass) {
      final PsiMethod[] methods = aClass.getMethods();
      int totalComplexity = calculateComplexityForMethods(methods);
      totalComplexity += calculateInitializerComplexity(aClass);
      return totalComplexity;
    }

    private static int calculateInitializerComplexity(PsiClass aClass) {
      final CyclomaticComplexityVisitor visitor = new CyclomaticComplexityVisitor();
      int complexity = 0;
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      for (final PsiClassInitializer initializer : initializers) {
        visitor.reset();
        initializer.accept(visitor);
        complexity += visitor.getComplexity();
      }
      return complexity;
    }

    private static int calculateComplexityForMethods(PsiMethod[] methods) {
      final CyclomaticComplexityVisitor visitor = new CyclomaticComplexityVisitor();
      int complexity = 0;
      for (final PsiMethod method : methods) {
        visitor.reset();
        method.accept(visitor);
        complexity += visitor.getComplexity();
      }
      return complexity;
    }
  }
}