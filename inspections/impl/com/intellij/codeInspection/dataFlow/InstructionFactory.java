/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
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

  public BinopInstruction createBinopInstruction(@NonNls String opSign, PsiElement psiAnchor) {
    return new BinopInstruction(opSign, psiAnchor);
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

  public EmptyInstruction createEmptyInstruction() {
    return new EmptyInstruction();
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
    return new MethodCallInstruction(context, factory, MethodCallInstruction.MethodType.CAST, castType);
  }

  public NotInstruction createNotInstruction() {
    return new NotInstruction();
  }

  public PopInstruction createPopInstruction() {
    return new PopInstruction();
  }

  public PushInstruction createPushInstruction(DfaValue value, final PsiExpression expression) {
    return new PushInstruction(value);
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

  public TypeCastInstruction createTypeCastInstruction() {
    return new TypeCastInstruction();
  }

  public TypeCastInstruction createTypeCastInstruction(PsiTypeCastExpression castExpression, DfaRelationValue instanceofRelation) {
    return new TypeCastInstruction(castExpression, instanceofRelation);
  }

}
