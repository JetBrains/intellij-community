/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class SuspiciousChainOperationsInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final int MIN_OPERAND_COUNT = 4;
  public static final EquivalenceChecker ourEquivalence = EquivalenceChecker.getCanonicalPsiEquivalence();

  private static HashSet comparisonOperations = new HashSet<>(Arrays.asList(JavaTokenType.ANDAND, JavaTokenType.OROR));


  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitPolyadicExpression(PsiPolyadicExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        if (!comparisonOperations.contains(tokenType)) return;
        PsiExpression[] operands = expression.getOperands();
        if (operands.length < MIN_OPERAND_COUNT) return;
        BinaryUnit firstUnit = BinaryUnit.extractEqualityCheck(operands[0]);
        if (firstUnit == null) return;
        BinaryUnitsChain chain = BinaryUnitsChain.createChain(firstUnit);
        if (chain == null) return;
        for (int i = 1; i < operands.length; i++) {
          PsiExpression operand = operands[i];
          BinaryUnit binaryUnit = BinaryUnit.extractEqualityCheck(operand);
          chain.add(binaryUnit);
        }
        PsiReferenceExpression reference = chain.getProbableBug();
        if (reference == null) return;
        holder.registerProblem(reference, InspectionsBundle.message("inspection.suspicious.chain.operation.description"));
      }


      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        BinaryUnit firstUnit = BinaryUnit.extractAssignment(expression);
        if (firstUnit == null) return;
        PsiExpressionStatement previous =
          tryCast(PsiTreeUtil.skipWhitespacesAndCommentsBackward(expression), PsiExpressionStatement.class);
        if (previous != null && BinaryUnit.extractAssignment(expression) != null) return; // starting from first assignment only
        PsiExpressionStatement parentStatement = tryCast(expression.getParent(), PsiExpressionStatement.class);
        if (parentStatement == null) return;
        PsiExpressionStatement next =
          tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(parentStatement), PsiExpressionStatement.class);
        BinaryUnitsChain chain = BinaryUnitsChain.createChain(firstUnit);
        if (chain == null) return;
        while (next != null) {
          BinaryUnit binaryUnit = BinaryUnit.extractAssignment(next.getExpression());
          if (binaryUnit == null) break;
          chain.add(binaryUnit);
          next = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(next), PsiExpressionStatement.class);
        }
        PsiReferenceExpression reference = chain.getProbableBug();
        if (reference == null) return;
        holder.registerProblem(reference, InspectionsBundle.message("inspection.suspicious.chain.operation.description"));
      }
    };
  }

  private static class BinaryUnit {
    final @NotNull PsiReferenceExpression myLeft;
    final @NotNull PsiReferenceExpression myRight;

    private BinaryUnit(@NotNull PsiReferenceExpression left, @NotNull PsiReferenceExpression right) {
      myLeft = left;
      myRight = right;
    }

    @NotNull
    String getRightName() {
      //noinspection ConstantConditions  checked at creation
      return myRight.getReferenceName();
    }

    @NotNull
    String getLeftName() {
      //noinspection ConstantConditions  checked at creation
      return myLeft.getReferenceName();
    }


    @Nullable
    static BinaryUnit extractAssignment(@Nullable PsiExpression expression) {
      PsiAssignmentExpression assignmentExpression = tryCast(expression, PsiAssignmentExpression.class);
      if (assignmentExpression == null) return null;
      if (!assignmentExpression.getOperationTokenType().equals(JavaTokenType.EQ)) return null;
      return extract(assignmentExpression.getLExpression(), assignmentExpression.getRExpression());
    }

    @Nullable
    static BinaryUnit extractEqualityCheck(@Nullable PsiExpression expression) {
      PsiBinaryExpression binaryExpression = tryCast(expression, PsiBinaryExpression.class);
      if (binaryExpression == null) return null;
      if (binaryExpression.getOperationTokenType() != JavaTokenType.EQEQ) return null;
      return extract(binaryExpression.getLOperand(), binaryExpression.getROperand());
    }

    @Nullable
    private static BinaryUnit extract(@Nullable PsiExpression left, @Nullable PsiExpression right) {
      PsiReferenceExpression lOperand = tryCast(left, PsiReferenceExpression.class);
      PsiReferenceExpression rOperand = tryCast(right, PsiReferenceExpression.class);
      if (lOperand == null || rOperand == null) return null;
      if (rOperand.getReferenceName() == null) return null;
      if (lOperand.getReferenceName() == null) return null;
      return new BinaryUnit(lOperand, rOperand);
    }
  }

  private static class BinaryUnitsChain {
    private final Map<String, List<BinaryUnit>> myRightNameToUnit = new HashMap<>();
    private final Map<String, List<BinaryUnit>> myLeftNameToUnit = new HashMap<>();
    private final PsiExpression myFirstLeftQualifier;
    private final PsiExpression myFirstRightQualifier;
    private int myCount = 0;

    private BinaryUnitsChain(@NotNull BinaryUnit first) {
      myFirstLeftQualifier = first.myLeft.getQualifierExpression();
      myFirstRightQualifier = first.myRight.getQualifierExpression();
      myRightNameToUnit.put(first.getRightName(), new ArrayList<>());
    }

    void add(@Nullable BinaryUnit unit) {
      if (unit == null) return;
      myCount++;
      if (!ourEquivalence.expressionsAreEquivalent(unit.myLeft.getQualifierExpression(), myFirstLeftQualifier)) return;
      if (!ourEquivalence.expressionsAreEquivalent(unit.myRight.getQualifierExpression(), myFirstRightQualifier)) return;
      myRightNameToUnit.computeIfAbsent(unit.getRightName(), k -> new ArrayList<>()).add(unit);
      myLeftNameToUnit.computeIfAbsent(unit.getLeftName(), k -> new ArrayList<>()).add(unit);
    }

    @Nullable
    PsiReferenceExpression getProbableBug() {
      if (myCount < MIN_OPERAND_COUNT) return null;
      PsiReferenceExpression leftUnit = getProbableBug(myRightNameToUnit, true);
      PsiReferenceExpression rightUnit = getProbableBug(myLeftNameToUnit, false);
      if (leftUnit != null && rightUnit != null) return null;
      if (leftUnit != null) return leftUnit;
      return rightUnit;
    }

    @Nullable
    private static PsiReferenceExpression getProbableBug(@NotNull Map<String, List<BinaryUnit>> nameToUnit, boolean isRightSide) {
      if (nameToUnit.size() < 2) return null;
      boolean sameNameGroupIsSingle =
        StreamEx.of(nameToUnit.values()).mapToInt(units -> units.size()).filter(size -> size > 1).count() == 1;
      if (!sameNameGroupIsSingle) return null;
      for (List<BinaryUnit> units : nameToUnit.values()) {
        if (units.size() > 1) {
          BinaryUnit unit = units.get(units.size() - 1);
          return isRightSide ? unit.myRight : unit.myLeft;
        }
      }
      return null;
    }

    @Nullable
    static BinaryUnitsChain createChain(@NotNull BinaryUnit first) {
      PsiExpression leftQualifier = first.myLeft.getQualifierExpression();
      PsiExpression rightQualifier = first.myRight.getQualifierExpression();
      if (leftQualifier == null || rightQualifier == null) return null;
      PsiClassType leftClassType = tryCast(leftQualifier.getType(), PsiClassType.class);
      PsiClassType rightClassType = tryCast(rightQualifier.getType(), PsiClassType.class);
      if (leftClassType == null || rightClassType == null) return null;
      PsiClass leftClass = leftClassType.resolve();
      PsiClass rightClass = rightClassType.resolve();
      if (leftClass == null || rightClass == null) return null;
      if (leftClass.isEnum() || rightClass.isEnum()) return null;
      return new BinaryUnitsChain(first);
    }
  }
}
