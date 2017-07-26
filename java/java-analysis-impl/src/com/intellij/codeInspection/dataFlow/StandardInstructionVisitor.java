/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.TypeUtils;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author peter
 */
public class StandardInstructionVisitor extends InstructionVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.StandardInstructionVisitor");
  private static final Object ANY_VALUE = new Object();

  private static final Set<String> OPTIONAL_METHOD_NAMES = ContainerUtil
    .set("of", "ofNullable", "fromNullable", "empty", "absent", "or", "orElse", "orElseGet", "ifPresent", "map", "flatMap", "filter",
         "transform");
  private static final CallMapper<LongRangeSet> KNOWN_METHOD_RANGES = new CallMapper<LongRangeSet>()
    .register(CallMatcher.instanceCall("java.time.LocalDateTime", "getHour"), LongRangeSet.range(0, 23))
    .register(CallMatcher.instanceCall("java.time.LocalDateTime", "getMinute", "getSecond"), LongRangeSet.range(0, 59))
    .register(CallMatcher.staticCall(CommonClassNames.JAVA_LANG_LONG, "numberOfLeadingZeros", "numberOfTrailingZeros", "bitCount"),
              LongRangeSet.range(0, Long.SIZE))
    .register(CallMatcher.staticCall(CommonClassNames.JAVA_LANG_INTEGER, "numberOfLeadingZeros", "numberOfTrailingZeros", "bitCount"),
              LongRangeSet.range(0, Integer.SIZE));

  private final Set<BinopInstruction> myReachable = new THashSet<>();
  private final Set<BinopInstruction> myCanBeNullInInstanceof = new THashSet<>();
  private final MultiMap<PushInstruction, Object> myPossibleVariableValues = MultiMap.createSet();
  private final Set<PsiElement> myNotToReportReachability = new THashSet<>();
  private final Set<InstanceofInstruction> myUsefulInstanceofs = new THashSet<>();

  @Override
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue dfaSource = memState.pop();
    DfaValue dfaDest = memState.pop();

    if (instruction.getAssignedValue() != null) {
      // It's possible that dfaDest on the stack is cleared to DfaTypeValue due to variable flush
      // (e.g. during StateMerger#mergeByFacts), so we try to restore the original destination.
      dfaDest = instruction.getAssignedValue();
    }

    if (dfaDest instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue) dfaDest;

      final PsiModifierListOwner psi = var.getPsiVariable();
      boolean forceDeclaredNullity = !(psi instanceof PsiParameter && psi.getParent() instanceof PsiParameterList);
      if (forceDeclaredNullity && var.getInherentNullability() == Nullness.NOT_NULL) {
        checkNotNullable(memState, dfaSource, NullabilityProblem.assigningToNotNull, instruction.getRExpression());
      }
      if (!(psi instanceof PsiField) || !psi.hasModifierProperty(PsiModifier.VOLATILE)) {
        memState.setVarValue(var, dfaSource);
      }
      if (var.getInherentNullability() == Nullness.NULLABLE && !memState.isNotNull(dfaSource) && instruction.isVariableInitializer()) {
        DfaMemoryStateImpl stateImpl = (DfaMemoryStateImpl)memState;
        stateImpl.setVariableState(var, stateImpl.getVariableState(var).withFact(DfaFactType.CAN_BE_NULL, true));
      }

    } else if (dfaDest instanceof DfaTypeValue && ((DfaTypeValue)dfaDest).isNotNull()) {
      checkNotNullable(memState, dfaSource, NullabilityProblem.assigningToNotNull, instruction.getRExpression());
    }

    memState.push(dfaDest);

    return nextInstruction(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction,
                                                     DataFlowRunner runner,
                                                     DfaMemoryState memState) {
    final DfaValue retValue = memState.pop();
    checkNotNullable(memState, retValue, NullabilityProblem.nullableReturn, instruction.getReturn());
    return nextInstruction(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitArrayAccess(ArrayAccessInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue index = memState.pop();
    DfaValue array = memState.pop();
    PsiArrayAccessExpression arrayExpression = instruction.getExpression();
    if (!checkNotNullable(memState, array, NullabilityProblem.fieldAccessNPE, arrayExpression.getArrayExpression())) {
      forceNotNull(runner, memState, array);
    }
    boolean alwaysOutOfBounds = false;
    if (index != DfaUnknownValue.getInstance()) {
      DfaValueFactory factory = runner.getFactory();
      DfaValue indexNonNegative =
        factory.createCondition(index, RelationType.GE, factory.getConstFactory().createFromValue(0, PsiType.INT, null));
      if (!memState.applyCondition(indexNonNegative)) {
        alwaysOutOfBounds = true;
      }
      DfaValue dfaLength = SpecialField.ARRAY_LENGTH.createValue(factory, array);
      if(dfaLength != null) {
        DfaValue indexLessThanLength = factory.createCondition(index, RelationType.LT, dfaLength);
        if (!memState.applyCondition(indexLessThanLength)) {
          alwaysOutOfBounds = true;
        }
      }
    }
    processArrayAccess(arrayExpression, alwaysOutOfBounds);
    memState.push(instruction.getValue());
    return nextInstruction(instruction, runner, memState);
  }

  protected void processArrayAccess(PsiArrayAccessExpression expression, boolean alwaysOutOfBounds) {

  }

  @Override
  public DfaInstructionState[] visitFieldReference(FieldReferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final DfaValue qualifier = memState.pop();
    if (!checkNotNullable(memState, qualifier, NullabilityProblem.fieldAccessNPE, instruction.getElementToAssert())) {
      forceNotNull(runner, memState, qualifier);
    }
    PsiElement parent = instruction.getExpression().getParent();
    if (parent instanceof PsiMethodReferenceExpression) {
      handleMethodReference(qualifier, (PsiMethodReferenceExpression)parent, runner, memState);
    }

    return nextInstruction(instruction, runner, memState);
  }

  private void handleMethodReference(DfaValue qualifier,
                                     PsiMethodReferenceExpression methodRef,
                                     DataFlowRunner runner,
                                     DfaMemoryState state) {
    PsiType functionalInterfaceType = methodRef.getFunctionalInterfaceType();
    if (functionalInterfaceType == null) return;
    PsiMethod sam = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    if (sam == null || PsiType.VOID.equals(sam.getReturnType())) return;
    JavaResolveResult resolveResult = methodRef.advancedResolve(false);
    PsiMethod method = ObjectUtils.tryCast(resolveResult.getElement(), PsiMethod.class);
    if (method == null || !ControlFlowAnalyzer.isPure(method)) return;
    List<? extends MethodContract> contracts = ControlFlowAnalyzer.getMethodCallContracts(method, null);
    if (contracts.isEmpty()) return;
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    DfaCallArguments callArguments = getMethodReferenceCallArguments(methodRef, qualifier, runner, sam, method, substitutor);
    PsiType returnType = substitutor.substitute(method.getReturnType());
    DfaValue defaultResult = runner.getFactory().createTypeValue(returnType, DfaPsiUtil.getElementNullability(returnType, method));
    Stream<DfaValue> returnValues = possibleReturnValues(callArguments, state, contracts, runner.getFactory(), defaultResult);
    returnValues.forEach(res -> processMethodReferenceResult(methodRef, contracts, res));
  }

  @NotNull
  private static DfaCallArguments getMethodReferenceCallArguments(PsiMethodReferenceExpression methodRef,
                                                                  DfaValue qualifier,
                                                                  DataFlowRunner runner,
                                                                  PsiMethod sam,
                                                                  PsiMethod method,
                                                                  PsiSubstitutor substitutor) {
    PsiParameter[] samParameters = sam.getParameterList().getParameters();
    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    boolean instanceBound = !isStatic && !PsiMethodReferenceUtil.isStaticallyReferenced(methodRef);
    PsiParameter[] parameters = method.getParameterList().getParameters();
    DfaValue[] arguments = new DfaValue[parameters.length];
    Arrays.fill(arguments, DfaUnknownValue.getInstance());
    for (int i = 0; i < samParameters.length; i++) {
      DfaValue value = runner.getFactory()
        .createTypeValue(substitutor.substitute(samParameters[i].getType()), DfaPsiUtil.getFunctionalParameterNullability(methodRef, i));
      if (i == 0 && !isStatic && !instanceBound) {
        qualifier = value;
      }
      else {
        int idx = i - ((isStatic || instanceBound) ? 0 : 1);
        if (idx >= arguments.length) break;
        if (!(parameters[idx].getType() instanceof PsiEllipsisType)) {
          arguments[idx] = value;
        }
      }
    }
    return new DfaCallArguments(qualifier, arguments);
  }

  private static Stream<DfaValue> possibleReturnValues(DfaCallArguments callArguments,
                                                       DfaMemoryState state,
                                                       List<? extends MethodContract> contracts,
                                                       DfaValueFactory factory, DfaValue defaultResult) {
    LinkedHashSet<DfaMemoryState> currentStates = ContainerUtil.newLinkedHashSet(state.createClosureState());
    Set<DfaMemoryState> finalStates = ContainerUtil.newLinkedHashSet();
    for (MethodContract contract : contracts) {
      DfaValue result = contract.getDfaReturnValue(factory, defaultResult);
      currentStates = addContractResults(callArguments, contract, currentStates, factory, finalStates, result);
    }
    return StreamEx.of(finalStates).map(DfaMemoryState::peek)
      .append(currentStates.isEmpty() ? StreamEx.empty() : StreamEx.of(defaultResult)).distinct();
  }

  protected void processMethodReferenceResult(PsiMethodReferenceExpression methodRef,
                                              List<? extends MethodContract> contracts,
                                              DfaValue res) {
  }

  @Override
  public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiExpression place = instruction.getPlace();
    if (!instruction.isReferenceWrite() && place instanceof PsiReferenceExpression) {
      DfaValue dfaValue = instruction.getValue();
      if (dfaValue instanceof DfaVariableValue) {
        DfaConstValue constValue = memState.getConstantValue((DfaVariableValue)dfaValue);
        boolean report = constValue != null && shouldReportConstValue(constValue.getValue(), place);
        myPossibleVariableValues.putValue(instruction, report ? constValue : ANY_VALUE);
      }
    }
    return super.visitPush(instruction, runner, memState);
  }

  private static boolean shouldReportConstValue(Object value, PsiElement place) {
    return value == null || value instanceof Boolean ||
           value.equals(new Long(0)) && isDivider(PsiUtil.skipParenthesizedExprUp(place));
  }

  private static boolean isDivider(PsiElement expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof PsiBinaryExpression) {
      return ControlFlowAnalyzer.isBinaryDivision(((PsiBinaryExpression)parent).getOperationTokenType()) &&
             ((PsiBinaryExpression)parent).getROperand() == expr;
    }
    if (parent instanceof PsiAssignmentExpression) {
      return ControlFlowAnalyzer.isAssignmentDivision(((PsiAssignmentExpression)parent).getOperationTokenType()) &&
             ((PsiAssignmentExpression)parent).getRExpression() == expr;
    }
    return false;
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
      DfaTypeValue dfaType = (DfaTypeValue)factory.createTypeValue(instruction.getCastTo(), Nullness.UNKNOWN);
      DfaRelationValue dfaInstanceof = factory.getRelationFactory().createRelation(dfaExpr, RelationType.IS, dfaType);
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
  public DfaInstructionState[] visitMethodCall(final MethodCallInstruction instruction, final DataFlowRunner runner, final DfaMemoryState memState) {
    Set<DfaMemoryState> finalStates = ContainerUtil.newLinkedHashSet();
    finalStates.addAll(handleOptionalMethods(instruction, runner, memState));
    finalStates.addAll(handleKnownMethods(instruction, runner, memState));

    if (finalStates.isEmpty()) {
      DfaCallArguments callArguments = popCall(instruction, runner, memState, true);

      LinkedHashSet<DfaMemoryState> currentStates = ContainerUtil.newLinkedHashSet(memState);
      if (callArguments.myArguments != null) {
        for (MethodContract contract : instruction.getContracts()) {
          DfaValue returnValue = getMethodResultValue(instruction, callArguments.myQualifier, runner.getFactory());
          returnValue = contract.getDfaReturnValue(runner.getFactory(), returnValue);
          currentStates = addContractResults(callArguments, contract, currentStates, runner.getFactory(), finalStates, returnValue);
          if (currentStates.size() + finalStates.size() > DataFlowRunner.MAX_STATES_PER_BRANCH) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Too complex contract on " + instruction.getContext() + ", skipping contract processing");
            }
            finalStates.clear();
            currentStates = ContainerUtil.newLinkedHashSet(memState);
            break;
          }
        }
      }
      for (DfaMemoryState state : currentStates) {
        state.push(getMethodResultValue(instruction, callArguments.myQualifier, runner.getFactory()));
        finalStates.add(state);
      }
    }

    DfaInstructionState[] result = new DfaInstructionState[finalStates.size()];
    int i = 0;
    for (DfaMemoryState state : finalStates) {
      if (instruction.shouldFlushFields()) {
        state.flushFields();
      }
      result[i++] = new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), state);
    }
    return result;
  }

  @NotNull
  private List<DfaMemoryState> handleKnownMethods(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiMethodCallExpression call = ObjectUtils.tryCast(instruction.getCallExpression(), PsiMethodCallExpression.class);
    CustomMethodHandlers.CustomMethodHandler handler = CustomMethodHandlers.find(call);
    if (handler == null) return Collections.emptyList();
    DfaCallArguments callArguments = popCall(instruction, runner, memState, false);
    List<DfaMemoryState> states =
      callArguments.myArguments == null ? Collections.emptyList() :
      handler.handle(callArguments, memState, runner.getFactory());
    if (states.isEmpty()) {
      memState.push(getMethodResultValue(instruction, callArguments.myQualifier, runner.getFactory()));
      return Collections.singletonList(memState);
    }
    return states;
  }

  @NotNull
  private List<DfaMemoryState> handleOptionalMethods(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiMethodCallExpression call = ObjectUtils.tryCast(instruction.getCallExpression(), PsiMethodCallExpression.class);
    if (call == null) return Collections.emptyList();
    String methodName = call.getMethodExpression().getReferenceName();
    if (methodName == null || !OPTIONAL_METHOD_NAMES.contains(methodName)) return Collections.emptyList();
    PsiMethod method = call.resolveMethod();
    if (method == null || !TypeUtils.isOptional(method.getContainingClass())) return Collections.emptyList();
    DfaCallArguments arguments = popCall(instruction, runner, memState, false);
    DfaValue[] argValues = arguments.myArguments;
    DfaValue result = null;
    DfaValueFactory factory = runner.getFactory();
    switch (methodName) {
      case "of":
      case "ofNullable":
      case "fromNullable":
        if ("of".equals(methodName) || (argValues != null && argValues.length == 1 && memState.isNotNull(argValues[0]))) {
          result = factory.getOptionalFactory().getOptional(true);
        }
        break;
      case "empty":
      case "absent":
        result = factory.getOptionalFactory().getOptional(false);
        break;
      case "orElse":
        if (argValues != null && argValues.length == 1) {
          DfaMemoryState falseState = memState.createCopy();
          DfaOptionalValue optional = factory.getOptionalFactory().getOptional(true);
          DfaValue relation = factory.createCondition(arguments.myQualifier, RelationType.IS, optional);
          List<DfaMemoryState> states = new ArrayList<>(2);
          if (memState.applyCondition(relation)) {
            memState.push(factory.createTypeValue(instruction.getResultType(), Nullness.NOT_NULL));
            states.add(memState);
          }
          if (falseState.applyCondition(relation.createNegated())) {
            falseState.push(argValues[0]);
            states.add(falseState);
          }
          return states;
        }
        break;
      case "filter":
      case "flatMap":
      case "ifPresent":
      case "map":
      case "or":
      case "orElseGet":
      case "transform": {
        DfaOptionalValue optional = factory.getOptionalFactory().getOptional(!methodName.startsWith("or"));
        DfaValue relation = factory.createCondition(arguments.myQualifier, RelationType.IS, optional);
        runner.updateStackTopClosures(state -> state.applyCondition(relation));
        break;
      }
      default:
    }
    memState.push(result == null ? getMethodResultValue(instruction, arguments.myQualifier, factory) : result);
    return Collections.singletonList(memState);
  }

  @NotNull
  private DfaCallArguments popCall(MethodCallInstruction instruction,
                                   DataFlowRunner runner,
                                   DfaMemoryState memState,
                                   boolean contractOnly) {
    DfaValue[] argValues = popCallArguments(instruction, runner, memState, contractOnly);
    final DfaValue qualifier = popQualifier(instruction, runner, memState);
    return new DfaCallArguments(qualifier, argValues);
  }

  @Nullable
  private DfaValue[] popCallArguments(MethodCallInstruction instruction,
                                      DataFlowRunner runner,
                                      DfaMemoryState memState,
                                      boolean contractOnly) {
    final int argCount = instruction.getArgCount();

    PsiMethod method = instruction.getTargetMethod();
    boolean varargCall = instruction.isVarArgCall();
    DfaValue[] argValues;
    if (method == null || (contractOnly && instruction.getContracts().isEmpty())) {
      argValues = null;
    } else {
      PsiParameterList paramList = method.getParameterList();
      int paramCount = paramList.getParametersCount();
      if (paramCount == argCount || method.isVarArgs() && argCount >= paramCount - 1) {
        argValues = new DfaValue[paramCount];
        if (varargCall) {
          argValues[paramCount - 1] = runner.getFactory().createTypeValue(paramList.getParameters()[paramCount - 1].getType(), Nullness.NOT_NULL);
        }
      } else {
        argValues = null;
      }
    }

    for (int i = 0; i < argCount; i++) {
      final DfaValue arg = memState.pop();
      int paramIndex = argCount - i - 1;
      if (argValues != null && (paramIndex < argValues.length - 1 || !varargCall)) {
        argValues[paramIndex] = arg;
      }

      PsiElement anchor = instruction.getArgumentAnchor(paramIndex);
      Nullness requiredNullability = instruction.getArgRequiredNullability(paramIndex);
      if (requiredNullability == Nullness.NOT_NULL) {
        if (!checkNotNullable(memState, arg, NullabilityProblem.passingNullableToNotNullParameter, anchor)) {
          forceNotNull(runner, memState, arg);
        }
      }
      else if (!instruction.updateOfNullable(memState, arg) && requiredNullability == Nullness.UNKNOWN) {
        checkNotNullable(memState, arg, NullabilityProblem.passingNullableArgumentToNonAnnotatedParameter, anchor);
      }
    }
    return argValues;
  }

  private DfaValue popQualifier(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    @NotNull final DfaValue qualifier = memState.pop();
    boolean unboxing = instruction.getMethodType() == MethodCallInstruction.MethodType.UNBOXING;
    NullabilityProblem problem = unboxing ? NullabilityProblem.unboxingNullable : NullabilityProblem.callNPE;
    PsiElement anchor = instruction.getContext();
    if (!checkNotNullable(memState, qualifier, problem, anchor)) {
      forceNotNull(runner, memState, qualifier);
    }
    return qualifier;
  }

  private static LinkedHashSet<DfaMemoryState> addContractResults(DfaCallArguments callArguments,
                                                                  MethodContract contract,
                                                                  LinkedHashSet<DfaMemoryState> states,
                                                                  DfaValueFactory factory,
                                                                  Set<DfaMemoryState> finalStates,
                                                                  DfaValue returnValue) {
    List<DfaValue> conditions = ContainerUtil.map(contract.getConditions(), cv -> cv.makeDfaValue(factory, callArguments));
    if (StreamEx.of(conditions).allMatch(factory.getConstFactory().getTrue()::equals)) {
      for (DfaMemoryState state : states) {
        state.push(returnValue);
        finalStates.add(state);
      }
      return new LinkedHashSet<>();
    }
    if (StreamEx.of(conditions).has(factory.getConstFactory().getFalse())) {
      return states;
    }

    LinkedHashSet<DfaMemoryState> falseStates = ContainerUtil.newLinkedHashSet();
    LinkedHashSet<DfaMemoryState> trueStates = ContainerUtil.newLinkedHashSet();

    for (DfaMemoryState state : states) {
      for (DfaValue condition : conditions) {
        if (condition == null) {
          condition = DfaUnknownValue.getInstance();
        }
        DfaMemoryState falseState = state.createCopy();
        if (falseState.applyContractCondition(condition.createNegated())) {
          falseStates.add(falseState);
        }
        if (!state.applyContractCondition(condition)) {
          state = null;
          break;
        }
      }
      if(state != null) {
        trueStates.add(state);
      }
    }

    for (DfaMemoryState state : trueStates) {
      state.push(returnValue);
      finalStates.add(state);
    }
    
    return falseStates;
  }

  private static void forceNotNull(DataFlowRunner runner, DfaMemoryState memState, DfaValue arg) {
    if (arg instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue)arg;
      memState.setVarValue(var, runner.getFactory().createTypeValue(var.getVariableType(), Nullness.NOT_NULL));
    }
  }

  @NotNull
  private static DfaValue getMethodResultValue(MethodCallInstruction instruction,
                                               @Nullable DfaValue qualifierValue,
                                               DfaValueFactory factory) {
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
      return boxed == null ? factory.createTypeValue(type, Nullness.NOT_NULL) : boxed;
    }

    if (methodType == MethodCallInstruction.MethodType.CAST) {
      assert qualifierValue != null;
      if (qualifierValue instanceof DfaConstValue) {
        Object casted = TypeConversionUtil.computeCastTo(((DfaConstValue)qualifierValue).getValue(), type);
        return factory.getConstFactory().createFromValue(casted, type, ((DfaConstValue)qualifierValue).getConstant());
      }
      return qualifierValue;
    }

    if (type != null && !(type instanceof PsiPrimitiveType)) {
      Nullness nullability = instruction.getReturnNullability();
      PsiMethod targetMethod = instruction.getTargetMethod();
      if (nullability == Nullness.UNKNOWN && targetMethod != null) {
        nullability = factory.suggestNullabilityForNonAnnotatedMember(targetMethod);
      }
      return factory.createTypeValue(type, nullability);
    }
    DfaRangeValue rangeValue = factory.getRangeFactory().create(type);
    if (rangeValue != null) {
      PsiCall call = instruction.getCallExpression();
      if (call instanceof PsiMethodCallExpression) {
        LongRangeSet range = KNOWN_METHOD_RANGES.mapFirst((PsiMethodCallExpression)call);
        if (range == null) {
          PsiMethod method = call.resolveMethod();
          if (method != null && AnnotationUtil.isAnnotated(method, "javax.annotation.Nonnegative", false)) {
            range = LongRangeSet.range(0, Long.MAX_VALUE);
          }
        }
        if (range != null) {
          return rangeValue.intersect(range);
        }
      }
      return rangeValue;
    }
    return DfaUnknownValue.getInstance();
  }

  protected boolean checkNotNullable(DfaMemoryState state,
                                     DfaValue value, NullabilityProblem problem,
                                     PsiElement anchor) {
    boolean notNullable = state.checkNotNullable(value);
    if (notNullable &&
        problem != NullabilityProblem.passingNullableArgumentToNonAnnotatedParameter) {
      DfaValueFactory factory = ((DfaMemoryStateImpl)state).getFactory();
      state.applyCondition(factory.createCondition(value, RelationType.NE, factory.getConstFactory().getNull()));
    }
    return notNullable;
  }

  @Override
  public DfaInstructionState[] visitCheckNotNull(CheckNotNullInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    checkNotNullable(memState, memState.peek(), NullabilityProblem.passingNullableToNotNullParameter, instruction.getExpression());
    return super.visitCheckNotNull(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    myReachable.add(instruction);

    DfaValue dfaRight = memState.pop();
    DfaValue dfaLeft = memState.pop();

    final IElementType opSign = instruction.getOperationSign();
    RelationType relationType = RelationType.fromElementType(opSign);
    if (relationType != null) {
      DfaInstructionState[] states = handleConstantComparison(instruction, runner, memState, dfaRight, dfaLeft, relationType);
      if (states == null) {
        states = handleRelationBinop(instruction, runner, memState, dfaRight, dfaLeft, relationType);
      }
      if (states != null) {
        return states;
      }
    }
    DfaValue result = null;
    if (JavaTokenType.AND == opSign) {
      LongRangeSet left = memState.getValueFact(DfaFactType.RANGE, dfaLeft);
      LongRangeSet right = memState.getValueFact(DfaFactType.RANGE, dfaRight);
      if(left != null && right != null) {
        result = runner.getFactory().getRangeFactory().create(left.bitwiseAnd(right));
      }
    }
    else if (JavaTokenType.PLUS == opSign) {
      result = instruction.getNonNullStringValue(runner.getFactory());
    }
    else {
      if (instruction instanceof InstanceofInstruction) {
        handleInstanceof((InstanceofInstruction)instruction, dfaRight, dfaLeft);
      }
    }
    memState.push(result == null ? DfaUnknownValue.getInstance() : result);

    instruction.setTrueReachable();  // Not a branching instruction actually.
    instruction.setFalseReachable();

    return nextInstruction(instruction, runner, memState);
  }

  @Nullable
  private DfaInstructionState[] handleRelationBinop(BinopInstruction instruction,
                                                    DataFlowRunner runner,
                                                    DfaMemoryState memState,
                                                    DfaValue dfaRight,
                                                    DfaValue dfaLeft,
                                                    RelationType relationType) {
    DfaValueFactory factory = runner.getFactory();
    final Instruction next = runner.getInstruction(instruction.getIndex() + 1);
    DfaValue condition = factory.createCondition(dfaLeft, relationType, dfaRight);
    if (condition instanceof DfaUnknownValue) return null;

    myCanBeNullInInstanceof.add(instruction);

    ArrayList<DfaInstructionState> states = new ArrayList<>(2);

    final DfaMemoryState trueCopy = memState.createCopy();
    if (trueCopy.applyCondition(condition)) {
      trueCopy.push(factory.getConstFactory().getTrue());
      instruction.setTrueReachable();
      states.add(new DfaInstructionState(next, trueCopy));
    }

    //noinspection UnnecessaryLocalVariable
    DfaMemoryState falseCopy = memState;
    if (falseCopy.applyCondition(condition.createNegated())) {
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

  private void handleInstanceof(InstanceofInstruction instruction, DfaValue dfaRight, DfaValue dfaLeft) {
    if (dfaLeft instanceof DfaTypeValue && dfaRight instanceof DfaTypeValue) {
      if (!((DfaTypeValue)dfaLeft).isNotNull()) {
        myCanBeNullInInstanceof.add(instruction);
      }

      if (((DfaTypeValue)dfaRight).getDfaType().isAssignableFrom(((DfaTypeValue)dfaLeft).getDfaType())) {
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
                                                                DfaValue dfaLeft, RelationType relationType) {
    if (dfaLeft instanceof DfaVariableValue && dfaRight instanceof DfaVariableValue) {
      Number leftValue = getKnownNumberValue(memState, (DfaVariableValue)dfaLeft);
      Number rightValue = getKnownNumberValue(memState, (DfaVariableValue)dfaRight);
      if (leftValue != null && rightValue != null) {
        return checkComparisonWithKnownValue(instruction, runner, memState, relationType, leftValue, rightValue);
      }
    }
    
    if (dfaRight instanceof DfaConstValue && dfaLeft instanceof DfaVariableValue) {
      Object value = ((DfaConstValue)dfaRight).getValue();
      if (value instanceof Number) {
        DfaInstructionState[] result = checkComparingWithConstant(instruction, runner, memState, (DfaVariableValue)dfaLeft, relationType,
                                                                  (Number)value);
        if (result != null) {
          return result;
        }
      }
    }
    if (dfaRight instanceof DfaVariableValue && dfaLeft instanceof DfaConstValue) {
      return handleConstantComparison(instruction, runner, memState, dfaLeft, dfaRight, relationType.getFlipped());
    }

    if (relationType != RelationType.EQ && relationType != RelationType.NE) {
      return null;
    }

    if (dfaLeft instanceof DfaConstValue && dfaRight instanceof DfaConstValue ||
        dfaLeft == runner.getFactory().getConstFactory().getContractFail() ||
        dfaRight == runner.getFactory().getConstFactory().getContractFail()) {
      boolean negated = (relationType == RelationType.NE) ^ (DfaMemoryStateImpl.isNaN(dfaLeft) || DfaMemoryStateImpl.isNaN(dfaRight));
      if (dfaLeft == dfaRight ^ negated) {
        return alwaysTrue(instruction, runner, memState);
      }
      return alwaysFalse(instruction, runner, memState);
    }

    return null;
  }

  @Nullable
  private static DfaInstructionState[] checkComparingWithConstant(BinopInstruction instruction,
                                                                  DataFlowRunner runner,
                                                                  DfaMemoryState memState,
                                                                  DfaVariableValue var,
                                                                  RelationType opSign, Number comparedWith) {
    Number knownValue = getKnownNumberValue(memState, var);
    if (knownValue != null) {
      return checkComparisonWithKnownValue(instruction, runner, memState, opSign, knownValue, comparedWith);
    }
    return null;
  }

  @Nullable
  private static Number getKnownNumberValue(DfaMemoryState memState, DfaVariableValue var) {
    DfaConstValue knownConstantValue = memState.getConstantValue(var);
    return knownConstantValue != null && knownConstantValue.getValue() instanceof Number ? (Number)knownConstantValue.getValue() : null;
  }

  private static DfaInstructionState[] checkComparisonWithKnownValue(BinopInstruction instruction,
                                                                     DataFlowRunner runner,
                                                                     DfaMemoryState memState,
                                                                     RelationType opSign,
                                                                     Number leftValue,
                                                                     Number rightValue) {
    int cmp = compare(leftValue, rightValue);
    Boolean result = null;
    boolean hasNaN = DfaUtil.isNaN(leftValue) || DfaUtil.isNaN(rightValue);
    if (cmp < 0 || cmp > 0) {
      if(opSign == RelationType.EQ) result = false;
      else if (opSign == RelationType.NE) result = true;
    }
    if (opSign == RelationType.LT) {
      result = !hasNaN && cmp < 0;
    }
    else if (opSign == RelationType.GT) {
      result = !hasNaN && cmp > 0;
    }
    else if (opSign == RelationType.LE) {
      result = !hasNaN && cmp <= 0;
    }
    else if (opSign == RelationType.GE) {
      result = !hasNaN && cmp >= 0;
    }
    if (result == null) {
      return null;
    }
    return result ? alwaysTrue(instruction, runner, memState) : alwaysFalse(instruction, runner, memState);
  }

  private static int compare(Number a, Number b) {
    long aLong = a.longValue();
    long bLong = b.longValue();
    if (aLong != bLong) return aLong > bLong ? 1 : -1;

    return Double.compare(a.doubleValue(), b.doubleValue());
  }

  private static DfaInstructionState[] alwaysFalse(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.push(runner.getFactory().getConstFactory().getFalse());
    instruction.setFalseReachable();
    return nextInstruction(instruction, runner, memState);
  }

  private static DfaInstructionState[] alwaysTrue(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.push(runner.getFactory().getConstFactory().getTrue());
    instruction.setTrueReachable();
    return nextInstruction(instruction, runner, memState);
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
    return false;
  }
}
