// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.jvm.problems.ArrayIndexProblem;
import com.intellij.codeInspection.dataFlow.jvm.problems.ArrayStoreProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Java-specific array store instruction. Additionally, it checks for assignability.
 */
public class JavaArrayStoreInstruction extends ArrayStoreInstruction {
  private final @NotNull PsiArrayAccessExpression myExpression;
  private final @Nullable PsiExpression myValueExpression;

  public JavaArrayStoreInstruction(@NotNull PsiArrayAccessExpression expression,
                                   @Nullable PsiExpression valueExpression,
                                   @Nullable DfaControlTransferValue outOfBoundsTransfer,
                                   @Nullable VariableDescriptor staticVariable) {
    super(createAnchor(expression), new ArrayIndexProblem(expression), outOfBoundsTransfer, staticVariable);
    myExpression = expression;
    myValueExpression = valueExpression;
  }

  private static @Nullable JavaExpressionAnchor createAnchor(@NotNull PsiArrayAccessExpression expression) {
    var assignment = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(expression.getParent()), PsiAssignmentExpression.class);
    return assignment == null ? null : new JavaExpressionAnchor(assignment);
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    DfaControlTransferValue transfer = myOutOfBoundsTransfer == null ? null : myOutOfBoundsTransfer.bindToFactory(factory);
    return new JavaArrayStoreInstruction(myExpression, myValueExpression, transfer, myStaticVariable);
  }

  @Override
  protected void checkArrayElementAssignability(@NotNull DataFlowInterpreter interpreter,
                                                @NotNull DfaMemoryState memState,
                                                @NotNull DfaValue dfaSource,
                                                @NotNull DfaValue qualifier) {
    if (myValueExpression == null) return;
    PsiType rCodeType = myValueExpression.getType();
    PsiType lCodeType = myExpression.getType();
    // If types known from source are not convertible, a compilation error is displayed, additional warning is unnecessary
    if (rCodeType == null || lCodeType == null || !TypeConversionUtil.areTypesConvertible(rCodeType, lCodeType)) return;
    DfType toType = TypeConstraint.fromDfType(memState.getDfType(qualifier)).getArrayComponentType();
    if (toType == DfType.BOTTOM) return;
    DfType fromType = memState.getDfType(dfaSource);
    DfType meet = fromType.meet(toType);
    Project project = interpreter.getFactory().getProject();
    PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(myValueExpression, PsiAssignmentExpression.class);
    PsiType psiFromType = TypeConstraint.fromDfType(fromType).getPsiType(project);
    PsiType psiToType = TypeConstraint.fromDfType(toType).getPsiType(project);
    if (assignmentExpression == null || psiFromType == null || psiToType == null) return;
    interpreter.getListener().onCondition(new ArrayStoreProblem(assignmentExpression, psiFromType, psiToType), dfaSource,
                                     meet == DfType.BOTTOM ? ThreeState.YES : ThreeState.UNSURE, memState);
  }
}
