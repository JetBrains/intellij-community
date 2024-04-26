// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.redundantCast;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.miscGenerics.SuspiciousMethodCallUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.siyeh.ig.format.FormatDecode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class RedundantCastInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  private final LocalQuickFix myQuickFixAction;
  @NonNls private static final String SHORT_NAME = "RedundantCast";

  public boolean IGNORE_SUSPICIOUS_METHOD_CALLS = true;

  public RedundantCastInspection() {
    myQuickFixAction = new AcceptSuggested();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel5OrHigher(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;

    return RedundantCastUtil.createRedundantCastVisitor(typeCast -> {
      ProblemDescriptor descriptor = createDescription(typeCast, holder.getManager(), isOnTheFly);
      if (descriptor != null) {
        holder.registerProblem(descriptor);
      }
      return true;
    });
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("IGNORE_SUSPICIOUS_METHOD_CALLS", JavaAnalysisBundle.message("ignore.casts.in.suspicious.collections.method.calls")));
  }

  @Nullable
  private ProblemDescriptor createDescription(@NotNull PsiTypeCastExpression cast, @NotNull InspectionManager manager, boolean onTheFly) {
    PsiExpression operand = cast.getOperand();
    PsiTypeElement castType = cast.getCastType();
    if (operand == null || castType == null) return null;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(cast.getParent());
    if (parent instanceof PsiExpressionList)  {
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiMethodCallExpression && IGNORE_SUSPICIOUS_METHOD_CALLS) {
        PsiType operandType = operand.getType();
        final String message = SuspiciousMethodCallUtil
          .getSuspiciousMethodCallMessage((PsiMethodCallExpression)gParent, operand, operandType, true, new ArrayList<>(), 0);
        if (message != null) {
          return null;
        }
        
        if (FormatDecode.isSuspiciousFormatCall((PsiMethodCallExpression)gParent, cast)) {
          return null;
        }
        
      }
    }

    String message = JavaAnalysisBundle.message("inspection.redundant.cast.problem.descriptor", PsiExpressionTrimRenderer.render(operand));
    return manager.createProblemDescriptor(castType, message, myQuickFixAction, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly);
  }


  private static class AcceptSuggested extends PsiUpdateModCommandQuickFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.redundant.cast.remove.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement castTypeElement, @NotNull ModPsiUpdater updater) {
      if (castTypeElement.getParent() instanceof PsiTypeCastExpression cast) {
        RemoveRedundantCastUtil.removeCast(cast);
      }
    }
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.verbose.or.redundant.code.constructs");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }
}
