// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.accessStaticViaInstance;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AccessStaticViaInstanceBase extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  public static final @NonNls String ACCESS_STATIC_VIA_INSTANCE = "AccessStaticViaInstance";

  @Override
  public @NotNull String getGroupDisplayName() {
    return "";
  }


  @Override
  public @NotNull @NonNls String getShortName() {
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
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        checkAccessStaticMemberViaInstanceReference(expression, holder);
      }
    };
  }

  private void checkAccessStaticMemberViaInstanceReference(PsiReferenceExpression expr, ProblemsHolder holder) {
    JavaResolveResult result = expr.advancedResolve(false);
    PsiElement resolved = result.getElement();

    if (!(resolved instanceof PsiMember member)) return;
    PsiExpression qualifierExpression = expr.getQualifierExpression();
    if (qualifierExpression == null) return;

    if (qualifierExpression instanceof PsiReferenceExpression) {
      final PsiElement qualifierResolved = ((PsiReferenceExpression)qualifierExpression).resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) {
        return;
      }
    }
    if (!member.hasModifierProperty(PsiModifier.STATIC)) return;

    //don't report warnings on compilation errors
    PsiClass containingClass = member.getContainingClass();
    if (containingClass != null && containingClass.isInterface() && member instanceof PsiMethod) return;
    if (containingClass instanceof PsiAnonymousClass || containingClass instanceof PsiImplicitClass) return;

    String description = JavaErrorBundle.message("static.member.accessed.via.instance.reference",
                                                 JavaHighlightUtil.formatType(qualifierExpression.getType()),
                                                 HighlightMessageUtil.getSymbolName(resolved, result.getSubstitutor()));
    holder.registerProblem(expr, description, createAccessStaticViaInstanceFix(expr, result));
  }

  protected LocalQuickFix createAccessStaticViaInstanceFix(PsiReferenceExpression expr, JavaResolveResult result) {
    return null;
  }
}
