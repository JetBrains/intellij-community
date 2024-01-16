// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class UseHashCodeMethodInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher DOUBLE_TO_LONG_BITS =
    CallMatcher.staticCall("java.lang.Double", "doubleToLongBits").parameterTypes("double");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
        HashCodeModel model = getHashCodeModel(expression);
        if (model != null) {
          PsiClass containingClass = ClassUtils.getContainingClass(expression);
          if (containingClass != null && containingClass.getQualifiedName() != null &&
              containingClass.getQualifiedName().startsWith("java.lang.")) {
            // Avoid suggesting inside JDK sources
            return;
          }
          holder.registerProblem(expression,
                                 JavaAnalysisBundle.message("inspection.can.be.replaced.with.message", model.type + ".hashCode()"),
                                 new ReplaceWithLongHashCodeFix(model.type));
        }
      }
    };
  }

  record HashCodeModel(@NotNull PsiExpression completeExpression,
                       @NotNull PsiExpression argument,
                       @Nullable PsiLocalVariable intermediateVariable,
                       @Nullable PsiExpression definition,
                       @NotNull String type) {
    @NotNull HashCodeModel tryReplaceDouble() {
      @Nullable PsiExpression expression = PsiUtil.skipParenthesizedExprDown(argument);
      if (!(expression instanceof PsiReferenceExpression referenceExpression)) return this;
      if (!(referenceExpression.resolve() instanceof PsiLocalVariable local)) return this;
      PsiExpression definition = PsiUtil.skipParenthesizedExprDown(DeclarationSearchUtils.findDefinition(referenceExpression, local));
      if (!(definition instanceof PsiMethodCallExpression call)) return this;
      if (!DOUBLE_TO_LONG_BITS.matches(call)) return this;
      PsiStatement statement = PsiTreeUtil.getParentOfType(definition, PsiStatement.class);
      PsiElement nextStatement = PsiTreeUtil.skipWhitespacesAndCommentsForward(statement);
      if (!PsiTreeUtil.isAncestor(nextStatement, completeExpression, true)) return this;
      final PsiCodeBlock block = PsiTreeUtil.getParentOfType(local, PsiCodeBlock.class);
      if (block == null || DefUseUtil.getRefs(block, local, definition).length != 2) return this;
      return new HashCodeModel(completeExpression, call.getArgumentList().getExpressions()[0], local, definition, "Double");
    }
  }

  private static @Nullable HashCodeModel getHashCodeModel(PsiTypeCastExpression cast) {
    if (cast == null) return null;
    if (!PsiTypes.intType().equals(cast.getType())) return null;
    PsiExpression operand = PsiUtil.skipParenthesizedExprDown(cast.getOperand());
    if (!(operand instanceof PsiBinaryExpression binaryExpression)) return null;

    PsiJavaToken operationSign = binaryExpression.getOperationSign();
    if (operationSign.getTokenType() != JavaTokenType.XOR) return null;

    PsiExpression leftOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
    PsiExpression rightOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());

    if (leftOperand == null || rightOperand == null) return null;
    if (!PsiTypes.longType().equals(PsiPrimitiveType.getOptionallyUnboxedType(leftOperand.getType()))) return null;

    if (isXorShift(leftOperand, rightOperand)) return new HashCodeModel(cast, leftOperand, null, null, "Long").tryReplaceDouble();
    if (isXorShift(rightOperand, leftOperand)) return new HashCodeModel(cast, rightOperand, null, null, "Long").tryReplaceDouble();
    return null;
  }

  private static boolean isXorShift(@NotNull PsiExpression leftOperand, @NotNull PsiExpression rightOperand) {
    if (rightOperand instanceof PsiBinaryExpression shiftingExpression) {
      if (shiftingExpression.getOperationSign().getTokenType() != JavaTokenType.GTGTGT) return false;

      PsiExpression leftSubOperand = shiftingExpression.getLOperand();
      return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(leftOperand, leftSubOperand) &&
             !SideEffectChecker.mayHaveSideEffects(leftOperand) &&
             Objects.equals(32, ExpressionUtils.computeConstantExpression(shiftingExpression.getROperand()));
    }

    return false;
  }

  public static class ReplaceWithLongHashCodeFix extends PsiUpdateModCommandQuickFix {
    private final String myType;

    public ReplaceWithLongHashCodeFix(String type) {
      myType = type;
    }

    @Override
    public @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myType+".hashCode()");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "hashCode()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      HashCodeModel model = getHashCodeModel((PsiTypeCastExpression)startElement);
      if (model == null) return;
      PsiExpression argument = model.argument();
      CommentTracker ct = new CommentTracker();
      String call = argument.getType() instanceof PsiPrimitiveType
                    ? "java.lang." + model.type() + ".hashCode(" + ct.text(argument) + ")"
                    : ct.text(argument, PsiPrecedenceUtil.METHOD_CALL_PRECEDENCE) + ".hashCode()";
      PsiLocalVariable local = model.intermediateVariable;
      if (local != null && model.definition != null) {
        PsiExpressionStatement expressionStatement = PsiTreeUtil.getParentOfType(model.definition, PsiExpressionStatement.class);
        if (expressionStatement != null) ct.delete(expressionStatement);
        List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(local);
        if (references.size() == 2) ct.delete(local);
      }
      ct.replaceAndRestoreComments(startElement, call);
    }
  }
}