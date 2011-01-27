/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Set;

/**
 * @author peter
 */
public class StandardInstructionVisitor extends InstructionVisitor {
  private final Set<BinopInstruction> myReachable = new THashSet<BinopInstruction>();
  private final Set<BinopInstruction> myCanBeNullInInstanceof = new THashSet<BinopInstruction>();
  private final Set<InstanceofInstruction> myUsefulInstanceofs = new THashSet<InstanceofInstruction>();
  private final FactoryMap<MethodCallInstruction, boolean[]> myParametersNotNull = new FactoryMap<MethodCallInstruction, boolean[]>() {
    @Override
    protected boolean[] create(MethodCallInstruction key) {
      final PsiCallExpression callExpression = key.getCallExpression();
      final PsiMethod callee = callExpression == null ? null : callExpression.resolveMethod();
      if (callee != null) {
        final PsiParameter[] params = callee.getParameterList().getParameters();
        boolean[] result = new boolean[params.length];
        for (int i = 0; i < params.length; i++) {
          result[i] = NullableNotNullManager.getInstance(params[i].getProject()).isNotNull(params[i], false);
        }
        return result;
      }
      else {
        return ArrayUtil.EMPTY_BOOLEAN_ARRAY;
      }
    }
  };
  private final FactoryMap<MethodCallInstruction, Boolean> myCalleeNullability = new FactoryMap<MethodCallInstruction, Boolean>() {
    @Override
    protected Boolean create(MethodCallInstruction key) {
      final PsiCallExpression callExpression = key.getCallExpression();
      if (callExpression instanceof PsiNewExpression) {
        return Boolean.FALSE;
      }

      if (callExpression != null) {
        final PsiMethod callee = callExpression.resolveMethod();
        if (callee != null) {
          if (AnnotationUtil.isNullable(callee)) {
            return Boolean.TRUE;
          }
          if (AnnotationUtil.isNotNull(callee)) {
            return Boolean.FALSE;
          }
        }
      }
      return null;
    }
  };

  @Override
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue dfaSource = memState.pop();
    DfaValue dfaDest = memState.pop();

    if (dfaDest instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue) dfaDest;
      final PsiVariable psiVariable = var.getPsiVariable();
      final NullableNotNullManager nullableManager = NullableNotNullManager.getInstance(psiVariable.getProject());
      if (nullableManager.isNotNull(psiVariable, false)) {
        if (!memState.applyNotNull(dfaSource)) {
          onAssigningToNotNullableVariable(instruction, runner);
        }
      }
      memState.setVarValue(var, dfaSource);
    }

    memState.push(dfaDest);

    return nextInstruction(instruction, runner, memState);
  }

  protected void onAssigningToNotNullableVariable(AssignInstruction instruction, DataFlowRunner runner) {}

  @Override
  public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction,
                                                     DataFlowRunner runner,
                                                     DfaMemoryState memState) {
    final DfaValue retValue = memState.pop();
    if (!memState.checkNotNullable(retValue)) {
      onNullableReturn(instruction, runner);
    }
    return nextInstruction(instruction, runner, memState);
  }

  protected void onNullableReturn(CheckReturnValueInstruction instruction, DataFlowRunner runner) {}

  @Override
  public DfaInstructionState[] visitFieldReference(FieldReferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final DfaValue qualifier = memState.pop();
    if (instruction.getExpression().isPhysical() && !memState.applyNotNull(qualifier)) {
      onInstructionProducesNPE(instruction, runner);
      return DfaInstructionState.EMPTY_ARRAY;
    }

    return nextInstruction(instruction, runner, memState);
  }

  protected void onInstructionProducesNPE(FieldReferenceInstruction instruction, DataFlowRunner runner) {}

  @Override
  public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final DfaValueFactory factory = runner.getFactory();
    DfaValue dfaExpr = factory.create(instruction.getCasted());
    if (dfaExpr != null) {
      DfaTypeValue dfaType = factory.getTypeFactory().create(instruction.getCastTo());
      DfaRelationValue dfaInstanceof = factory.getRelationFactory().create(dfaExpr, dfaType, "instanceof", false);
      if (dfaInstanceof != null && !memState.applyInstanceofOrNull(dfaInstanceof)) {
        onInstructionProducesCCE(instruction, runner);
      }
    }

    return nextInstruction(instruction, runner, memState);
  }

  protected void onInstructionProducesCCE(TypeCastInstruction instruction, DataFlowRunner runner) {}

  @Override
  public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final PsiExpression[] args = instruction.getArgs();
    final boolean[] parametersNotNull = myParametersNotNull.get(instruction);
    final DfaNotNullValue.Factory factory = runner.getFactory().getNotNullFactory();
    for (int i = 0; i < args.length; i++) {
      final DfaValue arg = memState.pop();
      final int revIdx = args.length - i - 1;
      if (args.length <= parametersNotNull.length && revIdx < parametersNotNull.length && parametersNotNull[revIdx] && !memState.applyNotNull(arg)) {
        onPassingNullParameter(runner, args[revIdx]);
        if (arg instanceof DfaVariableValue) {
          memState.setVarValue((DfaVariableValue)arg, factory.create(((DfaVariableValue)arg).getPsiVariable().getType()));
        }
      }
    }

    @NotNull final DfaValue qualifier = memState.pop();
    try {
      if (!memState.applyNotNull(qualifier)) {
        if (instruction.getMethodType() == MethodCallInstruction.MethodType.UNBOXING) {
          onUnboxingNullable(instruction, runner);
        }
        else {
          onInstructionProducesNPE(instruction, runner);
        }
        if (qualifier instanceof DfaVariableValue) {
          memState.setVarValue((DfaVariableValue)qualifier, factory.create(((DfaVariableValue)qualifier).getPsiVariable().getType()));
        }
      }

      return nextInstruction(instruction, runner, memState);
    }
    finally {
      pushResult(instruction, memState, qualifier, runner.getFactory());
      if (instruction.shouldFlushFields()) {
        memState.flushFields(runner);
      }
    }
  }

  private void pushResult(MethodCallInstruction instruction, DfaMemoryState state, final DfaValue oldValue, DfaValueFactory factory) {
    final PsiType type = instruction.getResultType();
    final MethodCallInstruction.MethodType methodType = instruction.getMethodType();
    DfaValue dfaValue = null;
    if (type != null && (type instanceof PsiClassType || type.getArrayDimensions() > 0)) {
      @Nullable final Boolean nullability = myCalleeNullability.get(instruction);
      dfaValue = nullability == Boolean.FALSE ? factory.getNotNullFactory().create(type) : factory.getTypeFactory().create(type, nullability == Boolean.TRUE);
    }
    else if (methodType == MethodCallInstruction.MethodType.UNBOXING) {
      dfaValue = factory.getBoxedFactory().createUnboxed(oldValue);
    }
    else if (methodType == MethodCallInstruction.MethodType.BOXING) {
      dfaValue = factory.getBoxedFactory().createBoxed(oldValue);
    }
    else if (methodType == MethodCallInstruction.MethodType.CAST) {
      if (oldValue instanceof DfaConstValue) {
        final DfaConstValue constValue = (DfaConstValue)oldValue;
        Object o = constValue.getValue();
        if (o instanceof Double || o instanceof Float) {
          double dbVal = o instanceof Double ? ((Double)o).doubleValue() : ((Float)o).doubleValue();
          // 5.0f == 5
          if (Math.floor(dbVal) == dbVal) o = TypeConversionUtil.computeCastTo(o, PsiType.LONG);
        }
        else {
          o = TypeConversionUtil.computeCastTo(o, PsiType.LONG);
        }

        dfaValue = factory.getConstFactory().createFromValue(o, type);
      }
      else {
        dfaValue = oldValue;
      }
    }

    state.push(dfaValue == null ? DfaUnknownValue.getInstance() : dfaValue);
  }


  protected void onInstructionProducesNPE(MethodCallInstruction instruction, DataFlowRunner runner) {}

  protected void onUnboxingNullable(MethodCallInstruction instruction, DataFlowRunner runner) {}

  protected void onPassingNullParameter(DataFlowRunner runner, PsiExpression arg) {}

  @Override
  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    myReachable.add(instruction);
    final Instruction next = runner.getInstruction(instruction.getIndex() + 1);

    DfaValue dfaRight = memState.pop();
    DfaValue dfaLeft = memState.pop();

    final String opSign = instruction.getOperationSign();
    if (opSign != null) {
      final DfaValueFactory factory = runner.getFactory();
      if (("==".equals(opSign) || "!=".equals(opSign)) &&
          dfaLeft instanceof DfaConstValue && dfaRight instanceof DfaConstValue) {
        boolean negated = "!=".equals(opSign) ^ (memState.canBeNaN(dfaLeft) || memState.canBeNaN(dfaRight));
        if (dfaLeft == dfaRight ^ negated) {
          memState.push(factory.getConstFactory().getTrue());
          instruction.setTrueReachable();
        }
        else {
          memState.push(factory.getConstFactory().getFalse());
          instruction.setFalseReachable();
        }
        return nextInstruction(instruction, runner, memState);
      }

      boolean negated = memState.canBeNaN(dfaLeft) || memState.canBeNaN(dfaRight);
      DfaRelationValue dfaRelation = factory.getRelationFactory().create(dfaLeft, dfaRight, opSign, negated);
      if (dfaRelation != null) {
        myCanBeNullInInstanceof.add(instruction);
        ArrayList<DfaInstructionState> states = new ArrayList<DfaInstructionState>();

        final DfaMemoryState trueCopy = memState.createCopy();
        if (trueCopy.applyCondition(dfaRelation)) {
          trueCopy.push(factory.getConstFactory().getTrue());
          instruction.setTrueReachable();
          states.add(new DfaInstructionState(next, trueCopy));
        }

        //noinspection UnnecessaryLocalVariable
        DfaMemoryState falseCopy = memState;
        if (falseCopy.applyCondition(dfaRelation.createNegated())) {
          falseCopy.push(factory.getConstFactory().getFalse());
          instruction.setFalseReachable();
          states.add(new DfaInstructionState(next, falseCopy));
          if (instruction instanceof InstanceofInstruction && !falseCopy.isNull(dfaLeft)) {
            myUsefulInstanceofs.add((InstanceofInstruction)instruction);
          }
        }

        return states.toArray(new DfaInstructionState[states.size()]);
      }
      else if ("+".equals(opSign)) {
        memState.push(instruction.getNonNullStringValue(factory));
        instruction.setTrueReachable();  // Not a branching instruction actually.
        instruction.setFalseReachable();
      }
      else {
        if (instruction instanceof InstanceofInstruction) {
          if ((dfaLeft instanceof DfaTypeValue || dfaLeft instanceof DfaNotNullValue) && dfaRight instanceof DfaTypeValue) {
            final PsiType leftType;
            if (dfaLeft instanceof DfaNotNullValue) {
              leftType = ((DfaNotNullValue)dfaLeft).getType();
            }
            else {
              leftType = ((DfaTypeValue)dfaLeft).getType();
              myCanBeNullInInstanceof.add(instruction);
            }

            if (!((DfaTypeValue)dfaRight).getType().isAssignableFrom(leftType)) {
              myUsefulInstanceofs.add((InstanceofInstruction)instruction);
            }
          }
          else {
            myUsefulInstanceofs.add((InstanceofInstruction)instruction);
          }
        }
        memState.push(DfaUnknownValue.getInstance());
      }
    }
    else {
      memState.push(DfaUnknownValue.getInstance());
    }

    return nextInstruction(instruction, runner, memState);
  }

  public boolean isInstanceofRedundant(InstanceofInstruction instruction) {
    return !myUsefulInstanceofs.contains(instruction) && !instruction.isConditionConst() && myReachable.contains(instruction);
  }

  public boolean canBeNull(BinopInstruction instruction) {
    return myCanBeNullInInstanceof.contains(instruction);
  }
}
