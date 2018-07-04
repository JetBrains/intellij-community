// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.accessStaticViaInstance;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class AccessStaticViaInstanceBase extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @NonNls public static final String ACCESS_STATIC_VIA_INSTANCE = "AccessStaticViaInstance";

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("access.static.via.instance");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return ACCESS_STATIC_VIA_INSTANCE;
  }

  @Override
  public String getAlternativeID() {
    return "static-access";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        checkAccessStaticMemberViaInstanceReference(expression, holder, isOnTheFly);
      }
    };
  }

  private void checkAccessStaticMemberViaInstanceReference(PsiReferenceExpression expr, ProblemsHolder holder, boolean onTheFly) {
    JavaResolveResult result = expr.advancedResolve(false);
    PsiElement resolved = result.getElement();

    if (!(resolved instanceof PsiMember)) return;
    PsiExpression qualifierExpression = expr.getQualifierExpression();
    if (qualifierExpression == null) return;

    if (qualifierExpression instanceof PsiReferenceExpression) {
      final PsiElement qualifierResolved = ((PsiReferenceExpression)qualifierExpression).resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) {
        return;
      }
    }
    if (!((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) return;

    //don't report warnings on compilation errors
    PsiClass containingClass = ((PsiMember)resolved).getContainingClass();
    if (containingClass != null && containingClass.isInterface()) return;

    String description = JavaErrorMessages.message("static.member.accessed.via.instance.reference",
                                                   JavaHighlightUtil.formatType(qualifierExpression.getType()),
                                                   HighlightMessageUtil.getSymbolName(resolved, result.getSubstitutor()));
    if (!onTheFly) {
      if (RemoveUnusedVariableUtil.checkSideEffects(qualifierExpression, null, new ArrayList<>())) {
        holder.registerProblem(expr, description);
        return;
      }
    }
    holder.registerProblem(expr, description, createAccessStaticViaInstanceFix(expr, onTheFly, result));
  }

  protected LocalQuickFix createAccessStaticViaInstanceFix(PsiReferenceExpression expr,
                                                           boolean onTheFly,
                                                           JavaResolveResult result) {
    return null;
  }
}
