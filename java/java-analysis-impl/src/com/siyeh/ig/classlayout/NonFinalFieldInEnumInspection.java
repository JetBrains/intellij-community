// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.MakeFieldFinalFix;
import com.siyeh.ig.psiutils.FinalUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class NonFinalFieldInEnumInspection extends BaseInspection {
  @SuppressWarnings("PublicField")
  public boolean onlyWarnWhenQuickFix = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyWarnWhenQuickFix", InspectionGadgetsBundle.message("non.final.field.in.enum.quickfix.option")));
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiClass enumClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message("non.final.field.in.enum.problem.descriptor", enumClass.getName());
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiField field = (PsiField)infos[1];
    return MakeFieldFinalFix.buildFix(field);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonFinalFieldInEnumVisitor();
  }

  private class NonFinalFieldInEnumVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null || !containingClass.isEnum()) {
        return;
      }
      if (onlyWarnWhenQuickFix && !FinalUtils.canBeFinal(field)) {
        return;
      }
      registerFieldError(field, containingClass, field);
    }
  }
}
