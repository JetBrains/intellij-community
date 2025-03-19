// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.optionalToIf;

import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.optionalToIf.Instruction.*;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

interface Simplifier {

  Simplifier[] SIMPLIFIERS = {
    new RemoveChecks(), new MergeChecks(), new RemoveAfterReturnOrThrow(), new MergeImmediateReturn(), new MergeImmediateAssignment()
  };

  List<Instruction> run(@NotNull List<Instruction> instructions);

  static @NotNull String simplify(@NotNull List<Instruction> instructions) {
    return StreamEx.of(SIMPLIFIERS).foldLeft(instructions, (acc, s) -> s.run(acc))
      .stream().map(Instruction::generate).collect(Collectors.joining());
  }

  class RemoveChecks implements Simplifier {

    @Override
    public List<Instruction> run(@NotNull List<Instruction> instructions) {
      List<Instruction> simplified = new ArrayList<>();
      for (Instruction instruction : instructions) {
        Check check = tryCast(instruction, Check.class);
        if (check != null && !check.hasElseBranch()) {
          Boolean res = check.myInstructions.isEmpty() ? Boolean.FALSE : DfaUtil.evaluateCondition(check.myCondition);
          if (Boolean.FALSE.equals(res)) continue;
          check.myInstructions = run(check.myInstructions);
          if (check.myInstructions.isEmpty() || Boolean.TRUE.equals(res)) {
            simplified.addAll(check.myInstructions);
            continue;
          }
        }
        simplified.add(instruction);
      }
      return simplified;
    }
  }

  class MergeChecks implements Simplifier {

    @Override
    public @NotNull List<Instruction> run(@NotNull List<Instruction> instructions) {
      List<Instruction> simplified = new ArrayList<>();
      Instruction prev = null;
      for (Instruction instruction : instructions) {
        Check check = tryCast(instruction, Check.class);
        if (check != null && !check.hasElseBranch()) {
          check.myInstructions = run(check.myInstructions);
          check = mergeWithInner(check);
          instruction = check;
          Check prevCheck = tryCast(prev, Check.class);
          if (prevCheck != null && !prevCheck.hasElseBranch()) {
            Check merged = mergeChecks(prevCheck, check);
            if (merged != null) {
              prev = merged;
              simplified.set(simplified.size() - 1, prev);
              continue;
            }
          }
        }
        prev = instruction;
        simplified.add(instruction);
      }
      return simplified;
    }

    private static @NotNull Check mergeWithInner(@NotNull Check check) {
      Check innerCheck = tryCast(getSingleInstruction(check), Check.class);
      if (innerCheck == null) return check;
      List<Instruction> checkInstructions = innerCheck.myInstructions;
      PsiExpression conjunction = mergeConditions(check, innerCheck, "&&");
      return new Check(conjunction, checkInstructions, null);
    }

    private static @NotNull PsiExpression mergeConditions(@NotNull Check c1, @NotNull Check c2, @NotNull String operator) {
      PsiExpression cond1 = c1.myCondition;
      PsiExpression cond2 = c2.myCondition;
      PsiElementFactory factory = PsiElementFactory.getInstance(cond1.getProject());
      return factory.createExpressionFromText(ParenthesesUtils.getText(cond1, ParenthesesUtils.OR_PRECEDENCE) +
                                              operator +
                                              ParenthesesUtils.getText(cond2, ParenthesesUtils.OR_PRECEDENCE), cond1);
    }

    private static @Nullable Check mergeChecks(@NotNull Instruction prev, @NotNull Check check) {
      Check prevCheck = tryCast(prev, Check.class);
      if (prevCheck == null) return null;
      Throw prevThrow = tryCast(getSingleInstruction(prevCheck), Throw.class);
      if (prevThrow == null) return null;
      Throw curThrow = tryCast(getSingleInstruction(check), Throw.class);
      if (curThrow == null) return null;
      boolean isSameException = EquivalenceChecker.getCanonicalPsiEquivalence()
        .expressionsAreEquivalent(prevThrow.myException, curThrow.myException);
      if (!isSameException) return null;
      PsiExpression disjunction = mergeConditions(prevCheck, check, "||");
      return new Check(disjunction, check.myInstructions, null);
    }

    private static Instruction getSingleInstruction(@NotNull Check check) {
      return ContainerUtil.getOnlyItem(check.myInstructions);
    }
  }

  /**
   * Removes all code that appears after throw or return instruction.
   * This might happen after applying other simplifications, in particular the ones that remove redundant checks.
   * E.g. for method
   * String test(String in) {
   *   if (in == null) return "foo";
   *   return Optional.ofNullable(in).orElse("bar");
   * }
   * We would have two instructions:
   * - Check(in != null) with Return(in) inside
   * - Return "bar"
   *
   * After simplification of Check that is always true we
   * end up with two returns in the row, so the second one must be removed.
   */
  class RemoveAfterReturnOrThrow implements Simplifier {

    @Override
    public List<Instruction> run(@NotNull List<Instruction> instructions) {
      List<Instruction> simplified = new ArrayList<>();
      for (Instruction instruction : instructions) {
        Check check = tryCast(instruction, Check.class);
        if (check != null && !check.hasElseBranch()) check.myInstructions = run(check.myInstructions);
        simplified.add(instruction);
        if (instruction instanceof Return || instruction instanceof Throw) return simplified;
      }
      return simplified;
    }
  }

  class MergeImmediateReturn implements Simplifier {

    @Override
    public List<Instruction> run(@NotNull List<Instruction> instructions) {
      List<Instruction> simplified = new ArrayList<>();
      Instruction prev = null;
      for (Instruction instruction : instructions) {
        Return ret = tryCast(instruction, Return.class);
        if (ret != null) {
          Return merged = mergeReturn(ret, prev);
          if (merged != null) {
            simplified.set(simplified.size() - 1, merged);
            prev = merged;
            continue;
          }
        }
        else {
          Check check = tryCast(instruction, Check.class);
          if (check != null && !check.hasElseBranch()) check.myInstructions = run(check.myInstructions);
        }
        simplified.add(instruction);
        prev = instruction;
      }
      return simplified;
    }

    private @Nullable Return mergeReturn(@NotNull Return ret, @Nullable Instruction prev) {
      PsiVariable retVariable = getReturnVariable(ret);
      if (retVariable == null) return null;
      Declaration declaration = tryCast(prev, Declaration.class);
      if (declaration != null) return mergeReturn(retVariable, declaration.myLhs, declaration.myRhs);
      Assignment assignment = tryCast(prev, Assignment.class);
      if (assignment != null) return mergeReturn(retVariable, assignment.myLhs, assignment.myRhs);
      return null;
    }

    @Nullable
    PsiVariable getReturnVariable(@NotNull Return ret) {
      PsiReference reference = tryCast(ret.myExpression, PsiReference.class);
      if (reference == null) return null;
      return tryCast(reference.resolve(), PsiVariable.class);
    }

    @Contract(pure = true)
    private static @Nullable Return mergeReturn(@NotNull PsiVariable retVariable, @NotNull PsiVariable lhs, @NotNull PsiExpression rhs) {
      return retVariable != lhs ? null : new Return(rhs);
    }
  }

  class MergeImmediateAssignment implements Simplifier {

    @Override
    public List<Instruction> run(@NotNull List<Instruction> instructions) {
      List<Instruction> simplified = new ArrayList<>();
      Instruction prev = null;
      for (Instruction instruction : instructions) {
        Assignment assignment = tryCast(instruction, Assignment.class);
        if (assignment != null) {
          Declaration declaration = tryCast(prev, Declaration.class);
          if (declaration != null && declaration.myLhs == assignment.myLhs) {
            Declaration merged = new Declaration(declaration.myLhs, assignment.myRhs);
            simplified.set(simplified.size() - 1, merged);
            prev = merged;
            continue;
          }
        }
        else {
          Check check = tryCast(instruction, Check.class);
          if (check != null && !check.hasElseBranch()) check.myInstructions = run(check.myInstructions);
        }
        simplified.add(instruction);
        prev = instruction;
      }
      return simplified;
    }
  }
}
