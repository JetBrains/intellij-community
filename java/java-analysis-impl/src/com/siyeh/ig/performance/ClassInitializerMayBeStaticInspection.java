// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.ChangeModifierFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public final class ClassInitializerMayBeStaticInspection extends BaseInspection {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "class.initializer.may.be.static.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ChangeModifierFix(PsiModifier.STATIC);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassInitializerCanBeStaticVisitor();
  }

  private static class ClassInitializerCanBeStaticVisitor extends BaseInspectionVisitor {
    @Override
    public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
      if (initializer.hasModifierProperty(PsiModifier.STATIC)) return;
      final PsiCodeBlock body = initializer.getBody();
      if (ControlFlowUtils.isEmptyCodeBlock(body)) {
        return;
      }
      final PsiClass containingClass =
        ClassUtils.getContainingClass(initializer);
      if (containingClass == null || containingClass instanceof PsiAnonymousClass) {
        return;
      }
      for (Condition<PsiElement> addin : InspectionManager.CANT_BE_STATIC_EXTENSION.getExtensionList()) {
        if (addin.value(initializer)) return;
      }
      final PsiElement scope = containingClass.getScope();
      if (!(scope instanceof PsiJavaFile) &&
          !containingClass.hasModifierProperty(PsiModifier.STATIC) &&
          !HighlightingFeature.INNER_STATICS.isAvailable(containingClass)) {
        return;
      }

      if (dependsOnInstanceMembers(initializer)) return;

      registerClassInitializerError(initializer);
    }
  }

  public static boolean dependsOnInstanceMembers(PsiClassInitializer initializer) {
    final MethodReferenceVisitor visitor = new MethodReferenceVisitor(initializer);
    initializer.accept(visitor);
    return !visitor.areReferencesStaticallyAccessible();
  }
}
