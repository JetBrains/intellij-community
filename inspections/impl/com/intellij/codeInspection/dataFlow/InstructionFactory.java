/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
public class InstructionFactory {
  public AssignInstruction createAssignInstruction(final PsiExpression RExpression) {
    return new AssignInstruction(RExpression);
  }

  public InstanceofInstruction createInstanceofInstruction(@NotNull PsiElement psiAnchor, @NotNull final PsiExpression left, @NotNull final PsiType castType) {
    return new InstanceofInstruction(psiAnchor, psiAnchor.getProject(), left, castType);
  }

  public BinopInstruction createBinopInstruction(@NonNls String opSign, PsiElement psiAnchor, Project project) {
    return new BinopInstruction(opSign, psiAnchor, project);
  }

  public CheckReturnValueInstruction createCheckReturnValueInstruction(final PsiReturnStatement aReturn) {
    return new CheckReturnValueInstruction(aReturn);
  }

  public ConditionalGotoInstruction createConditionalGotoInstruction(int myOffset, boolean isNegated, PsiElement psiAnchor) {
    return new ConditionalGotoInstruction(myOffset, isNegated, psiAnchor);
  }

  public DupInstruction createDupInstruction() {
    return new DupInstruction();
  }

  public EmptyStackInstruction createEmptyStackInstruction() {
    return new EmptyStackInstruction();
  }

  public FieldReferenceInstruction createFieldReferenceInstruction(PsiExpression expression, @Nullable @NonNls String syntheticFieldName) {
    return new FieldReferenceInstruction(expression, syntheticFieldName);
  }

  public FlushVariableInstruction createFlushVariableInstruction(DfaVariableValue expr) {
    return new FlushVariableInstruction(expr);
  }

  public GosubInstruction createGosubInstruction(int subprogramOffset) {
    return new GosubInstruction(subprogramOffset);
  }

  public GotoInstruction createGotoInstruction(int myOffset) {
    return new GotoInstruction(myOffset);
  }

  public MethodCallInstruction createMethodCallInstruction(@NotNull PsiCallExpression callExpression, DfaValueFactory factory) {
    return new MethodCallInstruction(callExpression, factory);
  }
  public MethodCallInstruction createMethodCallInstruction(@NotNull PsiExpression context, DfaValueFactory factory, MethodCallInstruction.MethodType methodType) {
    return new MethodCallInstruction(context, factory, methodType);
  }

  public MethodCallInstruction createCastInstruction(@NotNull PsiExpression context, DfaValueFactory factory, PsiType castType) {
    return new MethodCallInstruction(context, factory, MethodCallInstruction.MethodType.CAST, castType) {
      @Override
      public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
        return visitor.visitCast(this, runner, stateBefore);
      }
    };
  }

  public NotInstruction createNotInstruction() {
    return new NotInstruction();
  }

  public PopInstruction createPopInstruction() {
    return new PopInstruction();
  }

  public PushInstruction createPushInstruction(DfaValue value, final PsiExpression expression) {
    return new PushInstruction(value, expression);
  }

  public ReturnFromSubInstruction createReturnFromSubInstruction() {
    return new ReturnFromSubInstruction();
  }

  public ReturnInstruction createReturnInstruction() {
    return new ReturnInstruction();
  }

  public SwapInstruction createSwapInstruction() {
    return new SwapInstruction();
  }

  public Instruction createTypeCastInstruction() {
    return new Instruction() {
      @Override
      public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
        memState.pop();
        memState.push(DfaUnknownValue.getInstance());
        return super.apply(runner, memState);
      }

      @Override
      public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
        return apply(runner, stateBefore);
      }
    };
  }

  @NotNull
  public TypeCastInstruction createTypeCastInstruction(PsiTypeCastExpression castExpression, @NotNull PsiExpression casted, @NotNull PsiType toType, DfaValueFactory factory) {
    return new TypeCastInstruction(castExpression, casted, toType);
  }

}
