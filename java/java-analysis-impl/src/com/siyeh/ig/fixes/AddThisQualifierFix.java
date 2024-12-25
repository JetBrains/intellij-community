// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AddThisQualifierFix extends PsiUpdateModCommandQuickFix {

  private AddThisQualifierFix() {}

  public static @Nullable AddThisQualifierFix buildFix(PsiExpression expressionToQualify, PsiMember memberAccessed) {
    if (!isThisQualifierPossible(expressionToQualify, memberAccessed)) {
      return null;
    }
    return new AddThisQualifierFix();
  }

  private static boolean isThisQualifierPossible(PsiExpression memberAccessExpression, @NotNull PsiMember member) {
    if (member.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    final PsiClass memberClass = member.getContainingClass();
    if (memberClass == null) {
      return false;
    }
    PsiClass containingClass = PsiUtil.getContainingClass(memberAccessExpression);
    if (InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
      // unqualified this.
      return true;
    }
    do {
      containingClass = PsiUtil.getContainingClass(containingClass);
    }
    while (containingClass != null && !InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true));
    // qualified this needed, which is not possible on anonymous class.
    return containingClass != null && !(containingClass instanceof PsiAnonymousClass);
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("add.this.qualifier.quickfix");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiReferenceExpression expression = (PsiReferenceExpression)element;
    if (expression.getQualifierExpression() != null) {
      return;
    }
    final PsiExpression thisQualifier = ExpressionUtils.getEffectiveQualifier(expression);
    if (!(thisQualifier instanceof PsiThisExpression)) return;
    CommentTracker commentTracker = new CommentTracker();
    final @NonNls String newExpression = commentTracker.text(thisQualifier) + "." + commentTracker.text(expression);
    PsiReplacementUtil.replaceExpressionAndShorten(expression, newExpression, commentTracker);
  }
}
