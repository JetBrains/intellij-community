// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
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

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    final PsiElement context = (PsiElement)infos[1];
    return SuppressForTestsScopeFix.build(this, context);
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
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
