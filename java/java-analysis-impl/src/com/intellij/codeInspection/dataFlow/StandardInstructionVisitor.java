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

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.MultiMapBasedOnSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class StandardInstructionVisitor extends InstructionVisitor {
  private static final Object ANY_VALUE = new Object();
  private final Set<BinopInstruction> myReachable = new THashSet<BinopInstruction>();
  private final Set<BinopInstruction> myCanBeNullInInstanceof = new THashSet<BinopInstruction>();
  private final MultiMap<PushInstruction, Object> myPossibleVariableValues = new MultiMapBasedOnSet<PushInstruction, Object>();
  private final Set<PsiElement> myNotToReportReachability = new THashSet<PsiElement>();
  private final Set<InstanceofInstruction> myUsefulInstanceofs = new THashSet<InstanceofInstruction>();
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final FactoryMap<MethodCallInstruction, Map<PsiExpression, Nullness>> myParametersNullability = new FactoryMap<MethodCallInstruction, Map<PsiExpression, Nullness>>() {
    @Nullable
    @Override
    protected Map<PsiExpression, Nullness> create(MethodCallInstruction key) {
      return calcParameterNullability(key.getCallExpression());
    }
  };
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final FactoryMap<MethodCallInstruction, Nullness> myReturnTypeNullability = new FactoryMap<MethodCallInstruction, Nullness>() {
    @Override
    protected Nullness create(MethodCallInstruction key) {
      final PsiCallExpression callExpression = key.getCallExpression();
      if (callExpression instanceof PsiNewExpression) {
        return Nullness.NOT_NULL;
      }

      return callExpression != null ? DfaPsiUtil.getElementNullability(key.getResultType(), callExpression.resolveMethod()) : null;
    }
  };

  private static Map<PsiExpression, Nullness> calcParameterNullability(@Nullable PsiCallExpression callExpression) {
    PsiExpressionList argumentList = callExpression == null ? null : callExpression.getArgumentList();
    if (argumentList != null) {
      JavaResolveResult result = callExpression.resolveMethodGenerics();
      PsiMethod method = (PsiMethod)result.getElement();
      if (method != null) {
        PsiSubstitutor substitutor = result.getSubstitutor();
        PsiExpression[] args = argumentList.getExpressions();
        PsiParameter[] parameters = method.getParameterList().getParameters();

        boolean varArg = isVarArgCall(method, substitutor, args, parameters);
        int checkedCount = Math.min(args.length, parameters.length) - (varArg ? 1 : 0);

        Map<PsiExpression, Nullness> map = ContainerUtil.newHashMap();
        for (int i = 0; i < checkedCount; i++) {
          map.put(args[i], DfaPsiUtil.getElementNullability(substitutor.substitute(parameters[i].getType()), parameters[i]));
        }
        return map;
      }
    }
    return Collections.emptyMap();
  }

  private static boolean isVarArgCall(PsiMethod method, PsiSubstitutor substitutor, PsiExpression[] args, PsiParameter[] parameters) {
    if (!method.isVarArgs()) {
      return false;
    }

    int argCount = args.length;
    int paramCount = parameters.length;
    if (argCount > paramCount) {
      return true;
    }
    else if (paramCount > 0) {
      if (argCount == paramCount) {
        PsiType lastArgType = args[argCount - 1].getType();
        if (lastArgType != null &&
            !substitutor.substitute(parameters[paramCount - 1].getType()).isAssignableFrom(lastArgType)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue dfaSource = memState.pop();
    DfaValue dfaDest = memState.pop();

    if (dfaDest instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue) dfaDest;
      final PsiVariable psiVariable = var.getPsiVariable();
      if (DfaPsiUtil.getElementNullability(var.getVariableType(), psiVariable) == Nullness.NOT_NULL) {
        if (!memState.applyNotNull(dfaSource)) {
          onAssigningToNotNullableVariable(instruction);
        }
      }
      if (!(psiVariable instanceof PsiField) || !psiVariable.hasModifierProperty(PsiModifier.VOLATILE)) {
        memState.setVarValue(var, dfaSource);
      }
    } else if (dfaDest instanceof DfaNotNullValue && !memState.applyNotNull(dfaSource)) {
      onAssigningToNotNullableVariable(instruction);
    }

    memState.push(dfaDest);

    return nextInstruction(instruction, runner, memState);
  }

  protected void onAssigningToNotNullableVariable(AssignInstruction instruction) {}

  @Override
  public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction,
                                                     DataFlowRunner runner,
                                                     DfaMemoryState memState) {
    final DfaValue retValue = memState.pop();
    if (!memState.checkNotNullable(retValue)) {
      onNullableReturn(instruction);
    }
    return nextInstruction(instruction, runner, memState);
  }

  protected void onNullableReturn(CheckReturnValueInstruction instruction) {}

  @Override
  public DfaInstructionState[] visitFieldReference(FieldReferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final DfaValue qualifier = memState.pop();
    if (instruction.getExpression().isPhysical() && !memState.applyNotNull(qualifier)) {
      onInstructionProducesNPE(instruction);

      if (qualifier instanceof DfaVariableValue) {
        final DfaNotNullValue.Factory factory = runner.getFactory().getNotNullFactory();
        memState.setVarValue((DfaVariableValue)qualifier, factory.create(((DfaVariableValue)qualifier).getVariableType()));
      }
    }

    return nextInstruction(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    if (instruction.isReferenceRead()) {
      DfaValue dfaValue = instruction.getValue();
      if (dfaValue instanceof DfaVariableValue) {
        DfaConstValue constValue = memState.getConstantValue((DfaVariableValue)dfaValue);
        myPossibleVariableValues.putValue(instruction, constValue != null ? constValue : ANY_VALUE);
      }
    }
    return super.visitPush(instruction, runner, memState);
  }

  public List<Pair<PsiReferenceExpression, DfaConstValue>> getConstantReferenceValues() {
    List<Pair<PsiReferenceExpression, DfaConstValue>> result = ContainerUtil.newArrayList();
    for (PushInstruction instruction : myPossibleVariableValues.keySet()) {
      Collection<Object> values = myPossibleVariableValues.get(instruction);
      if (values.size() == 1) {
        Object singleValue = values.iterator().next();
        if (singleValue != ANY_VALUE) {
          result.add(Pair.create((PsiReferenceExpression)instruction.getPlace(), (DfaConstValue)singleValue));
        }
      }
    }
    return result;
  }

  @Override
  public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final DfaValueFactory factory = runner.getFactory();
    DfaValue dfaExpr = factory.createValue(instruction.getCasted());
    if (dfaExpr != null) {
      DfaTypeValue dfaType = factory.getTypeFactory().create(instruction.getCastTo());
      DfaRelationValue dfaInstanceof = factory.getRelationFactory().createRelation(dfaExpr, dfaType, JavaTokenType.INSTANCEOF_KEYWORD, false);
      if (dfaInstanceof != null && !memState.applyInstanceofOrNull(dfaInstanceof)) {
        onInstructionProducesCCE(instruction);
      }
    }

    if (instruction.getCastTo() instanceof PsiPrimitiveType) {
      memState.push(runner.getFactory().getBoxedFactory().createUnboxed(memState.pop()));
    }

    return nextInstruction(instruction, runner, memState);
  }

  protected void onInstructionProducesCCE(TypeCastInstruction instruction) {}

  @Override
  public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final PsiExpression[] args = instruction.getArgs();
    Map<PsiExpression, Nullness> map = myParametersNullability.get(instruction);
    final DfaNotNullValue.Factory factory = runner.getFactory().getNotNullFactory();
    for (int i = 0; i < args.length; i++) {
      final DfaValue arg = memState.pop();
      PsiExpression expr = args[(args.length - i - 1)];
      if (map.get(expr) == Nullness.NOT_NULL) {
        if (!memState.applyNotNull(arg)) {
          onPassingNullParameter(expr);
          if (arg instanceof DfaVariableValue) {
            memState.setVarValue((DfaVariableValue)arg, factory.create(((DfaVariableValue)arg).getVariableType()));
          }
        }
      }
      else if (map.get(expr) == Nullness.UNKNOWN && !memState.checkNotNullable(arg)) {
        onPassingNullParameterToNonAnnotated(runner, expr);
      }
    }

    @NotNull final DfaValue qualifier = memState.pop();
    try {
      if (!memState.applyNotNull(qualifier)) {
        onInstructionProducesNPE(instruction);
        if (qualifier instanceof DfaVariableValue) {
          memState.setVarValue((DfaVariableValue)qualifier, factory.create(((DfaVariableValue)qualifier).getVariableType()));
        }
      }

      return nextInstruction(instruction, runner, memState);
    }
    finally {
      memState.push(getMethodResultValue(instruction, qualifier, runner.getFactory()));
      if (instruction.shouldFlushFields()) {
        memState.flushFields(runner.getFields());
      }
    }
  }

  @NotNull
  private DfaValue getMethodResultValue(MethodCallInstruction instruction, @NotNull DfaValue qualifierValue, DfaValueFactory factory) {
    DfaValue precalculated = instruction.getPrecalculatedReturnValue();
    if (precalculated != null) {
      return precalculated;
    }

    final PsiType type = instruction.getResultType();
    final MethodCallInstruction.MethodType methodType = instruction.getMethodType();

    if (methodType == MethodCallInstruction.MethodType.UNBOXING) {
      return factory.getBoxedFactory().createUnboxed(qualifierValue);
    }

    if (methodType == MethodCallInstruction.MethodType.BOXING) {
      DfaValue boxed = factory.getBoxedFactory().createBoxed(qualifierValue);
      return boxed == null ? factory.getNotNullFactory().create(type) : boxed;
    }

    if (methodType == MethodCallInstruction.MethodType.CAST) {
      if (qualifierValue instanceof DfaConstValue) {
        return factory.getConstFactory().createFromValue(castConstValue((DfaConstValue)qualifierValue), type, ((DfaConstValue)qualifierValue).getConstant());
      }
      return qualifierValue;
    }

    if (type != null && (type instanceof PsiClassType || type.getArrayDimensions() > 0)) {
      return factory.createTypeValueWithNullability(type, myReturnTypeNullability.get(instruction));
    }
    return DfaUnknownValue.getInstance();
  }

  private static Object castConstValue(DfaConstValue constValue) {
    Object o = constValue.getValue();
    if (o instanceof Double || o instanceof Float) {
      double dbVal = o instanceof Double ? ((Double)o).doubleValue() : ((Float)o).doubleValue();
      // 5.0f == 5
      if (Math.floor(dbVal) != dbVal) {
        return o;
      }
    }

    return TypeConversionUtil.computeCastTo(o, PsiType.LONG);
  }


  protected void onInstructionProducesNPE(Instruction instruction) {}

  protected void onPassingNullParameter(PsiExpression arg) {}
  protected void onPassingNullParameterToNonAnnotated(DataFlowRunner runner, PsiExpression arg) {}

  @Override
  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    myReachable.add(instruction);

    DfaValue dfaRight = memState.pop();
    DfaValue dfaLeft = memState.pop();

    final IElementType opSign = instruction.getOperationSign();
    if (opSign != null) {
      DfaInstructionState[] states = handleConstantComparison(instruction, runner, memState, dfaRight, dfaLeft);
      if (states == null) {
        states = handleRelationBinop(instruction, runner, memState, dfaRight, dfaLeft);
      }
      if (states != null) {
        return states;
      }

      if (JavaTokenType.PLUS == opSign) {
        memState.push(instruction.getNonNullStringValue(runner.getFactory()));
      }
      else {
        if (instruction instanceof InstanceofInstruction) {
          handleInstanceof((InstanceofInstruction)instruction, dfaRight, dfaLeft);
        }
        memState.push(DfaUnknownValue.getInstance());
      }
    }
    else {
      memState.push(DfaUnknownValue.getInstance());
    }

    instruction.setTrueReachable();  // Not a branching instruction actually.
    instruction.setFalseReachable();

    return nextInstruction(instruction, runner, memState);
  }

  @Nullable
  private DfaInstructionState[] handleRelationBinop(BinopInstruction instruction,
                                                    DataFlowRunner runner,
                                                    DfaMemoryState memState,
                                                    DfaValue dfaRight, DfaValue dfaLeft) {
    DfaValueFactory factory = runner.getFactory();
    final Instruction next = runner.getInstruction(instruction.getIndex() + 1);
    DfaRelationValue dfaRelation = factory.getRelationFactory().createRelation(dfaLeft, dfaRight, instruction.getOperationSign(), false);
    if (dfaRelation == null) {
      return null;
    }

    if (isViaMethods(dfaLeft) || isViaMethods(dfaRight)) {
      skipConstantConditionReporting(instruction.getPsiAnchor());
    }
    myCanBeNullInInstanceof.add(instruction);

    ArrayList<DfaInstructionState> states = new ArrayList<DfaInstructionState>();

    final DfaMemoryState trueCopy = memState.createCopy();
    if (trueCopy.applyCondition(dfaRelation)) {
      if (!dfaRelation.isNegated()) {
        checkOneOperandNotNull(dfaRight, dfaLeft, factory, trueCopy);
      }
      trueCopy.push(factory.getConstFactory().getTrue());
      instruction.setTrueReachable();
      states.add(new DfaInstructionState(next, trueCopy));
    }

    //noinspection UnnecessaryLocalVariable
    DfaMemoryState falseCopy = memState;
    if (falseCopy.applyCondition(dfaRelation.createNegated())) {
      if (dfaRelation.isNegated()) {
        checkOneOperandNotNull(dfaRight, dfaLeft, factory, falseCopy);
      }
      falseCopy.push(factory.getConstFactory().getFalse());
      instruction.setFalseReachable();
      states.add(new DfaInstructionState(next, falseCopy));
      if (instruction instanceof InstanceofInstruction && !falseCopy.isNull(dfaLeft)) {
        myUsefulInstanceofs.add((InstanceofInstruction)instruction);
      }
    }

    return states.toArray(new DfaInstructionState[states.size()]);
  }

  public void skipConstantConditionReporting(@Nullable PsiElement anchor) {
    ContainerUtil.addIfNotNull(myNotToReportReachability, anchor);
  }

  private static boolean isViaMethods(DfaValue dfa) {
    return dfa instanceof DfaVariableValue && ((DfaVariableValue)dfa).isViaMethods();
  }

  private void handleInstanceof(InstanceofInstruction instruction, DfaValue dfaRight, DfaValue dfaLeft) {
    if ((dfaLeft instanceof DfaTypeValue || dfaLeft instanceof DfaNotNullValue) && dfaRight instanceof DfaTypeValue) {
      final PsiType leftType;
      if (dfaLeft instanceof DfaNotNullValue) {
        leftType = ((DfaNotNullValue)dfaLeft).getType();
      }
      else {
        leftType = ((DfaTypeValue)dfaLeft).getType();
        myCanBeNullInInstanceof.add(instruction);
      }

      if (((DfaTypeValue)dfaRight).getType().isAssignableFrom(leftType)) {
        return;
      }
    }
    myUsefulInstanceofs.add(instruction);
  }

  @Nullable
  private static DfaInstructionState[] handleConstantComparison(BinopInstruction instruction,
                                                                DataFlowRunner runner,
                                                                DfaMemoryState memState,
                                                                DfaValue dfaRight,
                                                                DfaValue dfaLeft) {
    final IElementType opSign = instruction.getOperationSign();
    if (JavaTokenType.EQEQ != opSign && JavaTokenType.NE != opSign ||
        !(dfaLeft instanceof DfaConstValue) || !(dfaRight instanceof DfaConstValue)) {
      return null;
    }

    boolean negated = (JavaTokenType.NE == opSign) ^ (DfaMemoryStateImpl.isNaN(dfaLeft) || DfaMemoryStateImpl.isNaN(dfaRight));
    if (dfaLeft == dfaRight ^ negated) {
      memState.push(runner.getFactory().getConstFactory().getTrue());
      instruction.setTrueReachable();
    }
    else {
      memState.push(runner.getFactory().getConstFactory().getFalse());
      instruction.setFalseReachable();
    }
    return nextInstruction(instruction, runner, memState);
  }

  private static void checkOneOperandNotNull(DfaValue var1, DfaValue var2, DfaValueFactory factory, DfaMemoryState state) {
    DfaValue nowNotNull = isNotNullExpression(var2, state) ? var1 : isNotNullExpression(var1, state) ? var2 : null;
    if (nowNotNull != null) {
      state.applyCondition(factory.getRelationFactory().createRelation(nowNotNull, factory.getConstFactory().getNull(), JavaTokenType.EQEQ, true));
    }
  }

  private static boolean isNotNullExpression(DfaValue dfa, DfaMemoryState state) {
    if (dfa instanceof DfaVariableValue) {
      return state.isNotNull((DfaVariableValue)dfa);
    }
    if (dfa instanceof DfaConstValue) {
      Object val = ((DfaConstValue)dfa).getValue();
      if (val instanceof PsiEnumConstant) {
        return true;
      }
    }
    return false;
  }

  public boolean isInstanceofRedundant(InstanceofInstruction instruction) {
    return !myUsefulInstanceofs.contains(instruction) && !instruction.isConditionConst() && myReachable.contains(instruction);
  }

  public boolean canBeNull(BinopInstruction instruction) {
    return myCanBeNullInInstanceof.contains(instruction);
  }

  public boolean silenceConstantCondition(@Nullable PsiElement element) {
    for (PsiElement skipped : myNotToReportReachability) {
      if (PsiTreeUtil.isAncestor(element, skipped, false)) {
        return true;
      }
    }
    if (PsiTreeUtil.findChildOfType(element, PsiAssignmentExpression.class) != null) {
      return true;
    }
    return false;
  }
}
