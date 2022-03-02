// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.style.ConditionalModel;
import com.siyeh.ig.style.IfConditionalModel;
import com.siyeh.ig.style.SimplifiableIfStatementInspection;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class ManualMinMaxCalculationInspection extends AbstractBaseJavaLocalInspectionTool {

  public boolean disableForNonIntegralTypes = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(JavaBundle.message("inspection.manual.min.max.calculation.disable.for.non.integral"),
                      "disableForNonIntegralTypes");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitIfStatement(PsiIfStatement statement) {
        ConditionalModel model = IfConditionalModel.from(statement, false);
        if (model == null) return;
        visitConditional(statement.getFirstChild(), model);
      }

      @Override
      public void visitConditionalExpression(PsiConditionalExpression expression) {
        ConditionalModel model = ConditionalModel.from(expression);
        if (model == null) return;
        visitConditional(expression, model);
      }

      private void visitConditional(@NotNull PsiElement element,
                                    @NotNull ConditionalModel model) {
        PsiBinaryExpression condition = getCondition(model.getCondition());
        if (condition == null) return;
        PsiExpression left = condition.getLOperand();
        if (SideEffectChecker.mayHaveSideEffects(left, e -> e instanceof PsiMethodCallExpression)) return;
        PsiType leftType = getType(left);
        if (leftType == null) return;
        PsiExpression right = condition.getROperand();
        if (right == null || SideEffectChecker.mayHaveSideEffects(right, e -> e instanceof PsiMethodCallExpression)) return;
        PsiType rightType = getType(right);
        if (rightType == null || leftType != rightType) return;
        EquivalenceChecker equivalenceChecker = EquivalenceChecker.getCanonicalPsiEquivalence();
        boolean useMathMin = equivalenceChecker.expressionsAreEquivalent(left, model.getElseExpression());
        if (!useMathMin && !equivalenceChecker.expressionsAreEquivalent(left, model.getThenExpression())) return;
        if (!equivalenceChecker.expressionsAreEquivalent(right, useMathMin ? model.getThenExpression() : model.getElseExpression())) return;
        IElementType tokenType = condition.getOperationTokenType();
        useMathMin ^= JavaTokenType.LT.equals(tokenType) || JavaTokenType.LE.equals(tokenType);
        holder.registerProblem(element,
                               JavaBundle.message("inspection.manual.min.max.calculation.description", useMathMin ? "min" : "max"),
                               new ReplaceWithMinMaxFix(useMathMin));
      }

      @Nullable
      private PsiType getType(@NotNull PsiExpression expression) {
        PsiType type = expression.getType();
        if (type == null) return null;
        int rank = TypeConversionUtil.getTypeRank(type);
        if (rank < TypeConversionUtil.INT_RANK || rank > TypeConversionUtil.DOUBLE_RANK) return null;
        return !disableForNonIntegralTypes || rank <= TypeConversionUtil.LONG_RANK ? type : null;
      }
    };
  }

  @Nullable
  private static PsiBinaryExpression getCondition(@Nullable PsiExpression expression) {
    PsiBinaryExpression condition = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiBinaryExpression.class);
    if (condition == null) return null;
    IElementType tokenType = condition.getOperationTokenType();
    if (JavaTokenType.LT.equals(tokenType) || JavaTokenType.LE.equals(tokenType) ||
        JavaTokenType.GT.equals(tokenType) || JavaTokenType.GE.equals(tokenType)) {
      return condition;
    }
    return null;
  }

  private static final class ReplaceWithMinMaxFix implements LocalQuickFix {

    private final boolean myUseMathMin;

    @Contract(pure = true)
    private ReplaceWithMinMaxFix(boolean useMathMin) {
      myUseMathMin = useMathMin;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x.call", "Math." + (myUseMathMin ? "min()" : "max()"));
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiConditionalExpression) {
        ConditionalModel model = ConditionalModel.from((PsiConditionalExpression)element);
        if (model == null) return;
        String replacement = createReplacement(model.getCondition());
        if (replacement == null) return;
        PsiReplacementUtil.replaceExpression((PsiExpression)element, replacement, new CommentTracker());
        return;
      }
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
      if (ifStatement == null) return;
      IfConditionalModel model = IfConditionalModel.from(ifStatement, false);
      if (model == null) return;
      String replacement = createReplacement(model.getCondition());
      if (replacement == null) return;
      PsiStatement elseBranch = model.getElseBranch();
      final PsiElement result;
      if (elseBranch instanceof PsiDeclarationStatement) {
        result = replace(ifStatement, elseBranch, model.getElseExpression(), replacement);
        elseBranch.delete();
      }
      else {
        result = replace(ifStatement, model.getThenBranch(), model.getThenExpression(), replacement);
        if (!PsiTreeUtil.isAncestor(ifStatement, elseBranch, true)) {
          new CommentTracker().deleteAndRestoreComments(elseBranch);
        }
      }
      SimplifiableIfStatementInspection.tryJoinDeclaration(result);
    }

    private static @NotNull PsiElement replace(@NotNull PsiIfStatement ifStatement,
                                               @NotNull PsiStatement branch, @NotNull PsiExpression toReplace,
                                               @NotNull String replacement) {
      PsiReplacementUtil.replaceExpression(toReplace, replacement, new CommentTracker());
      CommentTracker tracker = new CommentTracker();
      tracker.text(branch);
      return PsiReplacementUtil.replaceStatement(ifStatement, branch.getText(), tracker);
    }

    @Nullable
    private String createReplacement(@NotNull PsiExpression expression) {
      PsiBinaryExpression condition = getCondition(expression);
      if (condition == null) return null;
      PsiExpression left = condition.getLOperand();
      PsiExpression right = condition.getROperand();
      if (right == null) return null;
      return CommonClassNames.JAVA_LANG_MATH + (myUseMathMin ? ".min" : ".max") + "(" + left.getText() + "," + right.getText() + ")";
    }
  }
}
