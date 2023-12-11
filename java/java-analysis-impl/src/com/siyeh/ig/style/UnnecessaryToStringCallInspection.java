// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UnnecessaryToStringCallInspection extends BaseInspection implements CleanupLocalInspectionTool {

  public boolean notNullQualifierOnly = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("notNullQualifierOnly", JavaAnalysisBundle.message("inspection.redundant.tostring.option.notnull.qualifier")));
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.tostring.call.problem.descriptor");
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    final String text = (String)infos[0];
    return new UnnecessaryToStringCallFix(text);
  }

  private static final class UnnecessaryToStringCallFix extends PsiUpdateModCommandQuickFix {
    private final @Nullable String replacementText;

    private UnnecessaryToStringCallFix(@Nullable String replacementText) {
      this.replacementText = replacementText;
    }

    @Override
    @NotNull
    public String getName() {
      if (replacementText == null) {
        return InspectionGadgetsBundle.message("inspection.redundant.string.remove.fix.name", "toString");
      }
      return CommonQuickFixBundle.message("fix.replace.with.x", replacementText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.simplify");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiMethodCallExpression call =
        ObjectUtils.tryCast(startElement.getParent().getParent(), PsiMethodCallExpression.class);
      if (!isRedundantToString(call)) return;
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      final PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodExpression);
      if (qualifier == null) {
        // Should not happen normally as toString() should always resolve to the innermost class
        // Probably may happen only if SDK is broken (e.g. no java.lang.Object found)
        return;
      }
      new CommentTracker().replaceAndRestoreComments(call, qualifier);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryToStringCallVisitor();
  }

  private class UnnecessaryToStringCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      if (!isRedundantToString(call)) return;
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      PsiElement referenceNameElement = methodExpression.getReferenceNameElement();
      if (referenceNameElement == null) return;
      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodExpression);
      if (qualifier == null) return;
      if (notNullQualifierOnly && PsiTreeUtil.isAncestor(methodExpression, qualifier, true) &&
          NullabilityUtil.getExpressionNullability(qualifier, true) != Nullability.NOT_NULL) {
        return;
      }
      registerError(referenceNameElement, qualifier.isPhysical() ? null : qualifier.getText());
    }
  }

  @Contract("null -> false")
  private static boolean isRedundantToString(PsiMethodCallExpression call) {
    if (call == null) return false;
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String referenceName = methodExpression.getReferenceName();
    if (!"toString".equals(referenceName) || !call.getArgumentList().isEmpty()) return false;
    final PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodExpression);
    if (qualifier == null || qualifier.getType() instanceof PsiArrayType) {
      // do not warn on nonsensical code
      return false;
    }
    if (qualifier instanceof PsiSuperExpression) return false;
    final boolean throwable = TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_LANG_THROWABLE);
    return !ExpressionUtils.isConversionToStringNecessary(call, throwable);
  }
}
