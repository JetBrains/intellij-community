// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.problems.ArrayIndexProblem;
import com.intellij.codeInspection.dataFlow.jvm.problems.ArrayStoreProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Store array element. Pops (array, index, value) from the stack; pushes stored value
 */
public class ArrayStoreInstruction extends ExpressionPushingInstruction {
  private final @NotNull PsiArrayAccessExpression myExpression;
  private final @Nullable DfaControlTransferValue myOutOfBoundsTransfer;
  private final @Nullable PsiExpression myValueExpression;

  public ArrayStoreInstruction(@NotNull PsiArrayAccessExpression expression,
                               @Nullable PsiExpression valueExpression,
                               @Nullable DfaControlTransferValue outOfBoundsTransfer) {
    super(createAnchor(expression));
    myOutOfBoundsTransfer = outOfBoundsTransfer;
    myExpression = expression;
    myValueExpression = valueExpression;
  }

  private static @Nullable JavaExpressionAnchor createAnchor(@NotNull PsiArrayAccessExpression expression) {
    var assignment = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(expression.getParent()), PsiAssignmentExpression.class);
    return assignment == null ? null : new JavaExpressionAnchor(assignment);
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    if (myOutOfBoundsTransfer == null) return this;
    var instruction = new ArrayStoreInstruction(myExpression, myValueExpression, myOutOfBoundsTransfer.bindToFactory(factory));
    instruction.setIndex(getIndex());
    return instruction;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue valueToStore = stateBefore.pop();
    DfaValue index = stateBefore.pop();
    DfaValue array = stateBefore.pop();
    DfaInstructionState[] states =
      new ArrayIndexProblem(myExpression).processOutOfBounds(interpreter, stateBefore, index, array, myOutOfBoundsTransfer);
    if (states != null) return states;

    checkArrayElementAssignability(interpreter, stateBefore, valueToStore, array);

    LongRangeSet rangeSet = DfIntType.extractRange(stateBefore.getDfType(index));
    DfaValue arrayElementValue = ArrayElementDescriptor.getArrayElementValue(interpreter.getFactory(), array, rangeSet);
    interpreter.getListener().beforeAssignment(valueToStore, arrayElementValue, stateBefore, getDfaAnchor());
    if (arrayElementValue instanceof DfaVariableValue) {
      stateBefore.setVarValue((DfaVariableValue)arrayElementValue, valueToStore);
      pushResult(interpreter, stateBefore, arrayElementValue);
    }
    else {
      stateBefore.flushFieldsQualifiedBy(Set.of(array));
      pushResult(interpreter, stateBefore, valueToStore);
    }
    return nextStates(interpreter, stateBefore);
  }

  private void checkArrayElementAssignability(@NotNull DataFlowInterpreter runner,
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
    Project project = myExpression.getProject();
    PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(myValueExpression, PsiAssignmentExpression.class);
    PsiType psiFromType = TypeConstraint.fromDfType(fromType).getPsiType(project);
    PsiType psiToType = TypeConstraint.fromDfType(toType).getPsiType(project);
    if (assignmentExpression == null || psiFromType == null || psiToType == null) return;
    runner.getListener().onCondition(new ArrayStoreProblem(assignmentExpression, psiFromType, psiToType), dfaSource,
                                     meet == DfType.BOTTOM ? ThreeState.YES : ThreeState.UNSURE, memState);
  }

  @Override
  public List<DfaVariableValue> getWrittenVariables(DfaValueFactory factory) {
    return ContainerUtil.createMaybeSingletonList(ObjectUtils.tryCast(JavaDfaValueFactory.getExpressionDfaValue(factory, myExpression), 
                                                                      DfaVariableValue.class));
  }

  @Override
  public String toString() {
    return "ARRAY_STORE " + myExpression.getText();
  }
}
