/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.abstraction;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public final class FeatureEnvyInspection extends BaseInspection {

  @SuppressWarnings({"PublicField", "UnusedDeclaration"})
  public boolean ignoreTestCases = false; // keep for compatibility

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiElement context = (PsiElement)infos[1];
    return SuppressForTestsScopeFix.build(this, context);
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiNamedElement element = (PsiNamedElement)infos[0];
    final String className = element.getName();
    return InspectionGadgetsBundle.message("feature.envy.problem.descriptor", className);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FeatureEnvyVisitor();
  }

  private static class FeatureEnvyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      final PsiClass containingClass = method.getContainingClass();
      final ClassAccessVisitor visitor = new ClassAccessVisitor(containingClass);
      method.accept(visitor);
      final Set<PsiClass> overAccessedClasses = visitor.getOveraccessedClasses();
      for (PsiClass aClass : overAccessedClasses) {
        registerMethodError(method, aClass, method);
      }
    }
  }
}
