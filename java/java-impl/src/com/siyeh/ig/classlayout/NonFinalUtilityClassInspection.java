// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.MakeClassFinalFix;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class NonFinalUtilityClassInspection extends BaseInspection {

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new MakeClassFinalFix((PsiClass)infos[0]);
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.final.utility.class.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonFinalUtilityClassVisitor();
  }

  private static class NonFinalUtilityClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (!UtilityClassUtil.isUtilityClass(aClass)) {
        return;
      }

      if (aClass.hasModifierProperty(PsiModifier.FINAL) ||
          aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }

      final Query<PsiClass> query = ClassInheritorsSearch.search(aClass, false);
      final PsiClass subclass = query.findFirst();
      if (subclass != null) {
        return;
      }

      registerClassError(aClass, aClass);
    }
  }
}
