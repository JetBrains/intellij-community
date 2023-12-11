// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
                       @NotNull String type) {
    @NotNull HashCodeModel tryReplaceDouble() {
      PsiLocalVariable local = ExpressionUtils.resolveLocalVariable(argument);
      if (local == null) return this;
      if (!(PsiUtil.skipParenthesizedExprDown(local.getInitializer()) instanceof PsiMethodCallExpression call)) return this;
      if (!DOUBLE_TO_LONG_BITS.matches(call)) return this;
      if (!(local.getParent() instanceof PsiDeclarationStatement decl) || decl.getDeclaredElements().length != 1) return this;
      PsiElement nextDeclaration = PsiTreeUtil.skipWhitespacesAndCommentsForward(decl);
      if (!PsiTreeUtil.isAncestor(nextDeclaration, completeExpression, true)) return this;
      if (ContainerUtil.exists(VariableAccessUtils.getVariableReferences(local, PsiUtil.getVariableCodeBlock(local, null)),
                               ref -> !PsiTreeUtil.isAncestor(completeExpression, ref, true))) {
        return this;
      }
      return new HashCodeModel(completeExpression, call.getArgumentList().getExpressions()[0], local, "Double");
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

    if (isXorShift(leftOperand, rightOperand)) return new HashCodeModel(cast, leftOperand, null, "Long").tryReplaceDouble();
    if (isXorShift(rightOperand, leftOperand)) return new HashCodeModel(cast, rightOperand, null, "Long").tryReplaceDouble();
    return null;
  }

  private static boolean isXorShift(@NotNull PsiExpression leftOperand, @NotNull PsiExpression rightOperand) {
    if (rightOperand instanceof PsiBinaryExpression shiftingExpression) {
      if (shiftingExpression.getOperationSign().getTokenType() != JavaTokenType.GTGTGT) return false;

      PsiExpression leftSubOperand = shiftingExpression.getLOperand();
      return EquivalenceChecker.getCanonicalPsiEquivalence()
               .expressionsAreEquivalent(leftOperand, leftSubOperand) &&
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
      PsiTypeCastExpression element = (PsiTypeCastExpression)startElement;
      HashCodeModel model = getHashCodeModel(element);
      if (model == null) return;
      PsiExpression argument = model.argument();
      PsiType type = argument.getType();
      CommentTracker ct = new CommentTracker();
      String call = type instanceof PsiPrimitiveType
                    ? "java.lang." + model.type() + ".hashCode(" + ct.text(argument) + ")"
                    : ct.text(argument, PsiPrecedenceUtil.METHOD_CALL_PRECEDENCE) + ".hashCode()";
      if (model.intermediateVariable() != null) {
        ct.delete(model.intermediateVariable());
      }
      ct.replaceAndRestoreComments(element, call);
    }
  }
}