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
package com.intellij.codeInspection.dataFlow.inliner;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.StreamApiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

public class StreamChainInliner implements CallInliner {
  private static final String[] TERMINALS =
    {"count", "sum", "summaryStatistics", "reduce", "collect", "findFirst", "findAny", "anyMatch", "allMatch", "noneMatch", "toArray",
      "average", "forEach", "forEachOrdered", "min", "max", "toList", "toSet"};
  private static final CallMatcher TERMINAL_CALL = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, TERMINALS);

  private static final CallMatcher FOR_TERMINAL = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "forEach", "forEachOrdered").parameterCount(1);
  private static final CallMatcher MATCH_TERMINAL = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "anyMatch", "allMatch",
                                                                 "noneMatch").parameterCount(1);
  private static final CallMatcher SUM_TERMINAL = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "sum", "count").parameterCount(0);
  private static final CallMatcher OPTIONAL_TERMINAL =
    anyOf(instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "min", "max").parameterCount(0),
          instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "reduce").parameterCount(1),
          instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "findFirst", "findAny").parameterCount(0));
  private static final CallMatcher MIN_MAX_TERMINAL =
    instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "min", "max", "reduce").parameterCount(1);

  private static final CallMatcher SKIP_STEP =
    instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "unordered", "parallel", "sequential").parameterCount(0);
  private static final CallMatcher SORTED = anyOf(instanceCall(JAVA_UTIL_STREAM_STREAM, "sorted").parameterCount(1),
                                                  instanceCall(JAVA_UTIL_STREAM_STREAM, "sorted").parameterCount(0));
  private static final CallMatcher FILTER = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "filter").parameterCount(1);
  private static final CallMatcher STATE_FILTER = anyOf(instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "distinct").parameterCount(0),
                                                        instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "skip", "limit").parameterCount(1));
  private static final CallMatcher BOXED = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "boxed").parameterCount(0);
  private static final CallMatcher MAP =
    instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "map", "mapToInt", "mapToLong", "mapToDouble", "mapToObj").parameterCount(1);
  private static final CallMatcher FLAT_MAP =
    instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "flatMap", "flatMapToInt", "flatMapToLong", "flatMapToDouble").parameterCount(1);
  private static final CallMatcher PEEK = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "peek").parameterCount(1);

  private static final CallMatcher STREAM_GENERATE = anyOf(
    staticCall(JAVA_UTIL_STREAM_STREAM, "generate").parameterCount(1),
    staticCall(JAVA_UTIL_STREAM_INT_STREAM, "generate").parameterCount(1),
    staticCall(JAVA_UTIL_STREAM_LONG_STREAM, "generate").parameterCount(1),
    staticCall(JAVA_UTIL_STREAM_DOUBLE_STREAM, "generate").parameterCount(1));
  private static final CallMatcher STREAM_EMPTY = anyOf(
    staticCall(JAVA_UTIL_STREAM_STREAM, "empty").parameterCount(0),
    staticCall(JAVA_UTIL_STREAM_INT_STREAM, "empty").parameterCount(0),
    staticCall(JAVA_UTIL_STREAM_LONG_STREAM, "empty").parameterCount(0),
    staticCall(JAVA_UTIL_STREAM_DOUBLE_STREAM, "empty").parameterCount(0));
  private static final CallMatcher STREAM_OF = anyOf(
    staticCall(JAVA_UTIL_STREAM_STREAM, "of").parameterTypes("T"),
    staticCall(JAVA_UTIL_STREAM_INT_STREAM, "of").parameterTypes("int"),
    staticCall(JAVA_UTIL_STREAM_LONG_STREAM, "of").parameterTypes("long"),
    staticCall(JAVA_UTIL_STREAM_DOUBLE_STREAM, "of").parameterTypes("double"));
  private static final CallMatcher STREAM_OF_ARRAY = anyOf(
    staticCall(JAVA_UTIL_STREAM_STREAM, "of").parameterTypes("T[]"),
    staticCall(JAVA_UTIL_STREAM_INT_STREAM, "of").parameterTypes("int[]"),
    staticCall(JAVA_UTIL_STREAM_LONG_STREAM, "of").parameterTypes("long[]"),
    staticCall(JAVA_UTIL_STREAM_DOUBLE_STREAM, "of").parameterTypes("double[]"));
  private static final CallMatcher ARRAYS_STREAM = staticCall(JAVA_UTIL_ARRAYS, "stream").parameterCount(1);
  private static final CallMatcher COLLECTION_STREAM = instanceCall(JAVA_UTIL_COLLECTION, "stream").parameterCount(0);

  private static final CallMapper<UnaryOperator<Step>> INTERMEDIATE_STEP_MAPPER = new CallMapper<UnaryOperator<Step>>()
    .register(FILTER, (PsiMethodCallExpression call) -> (Step next) -> new FilterStep(call, next))
    .register(MAP, (PsiMethodCallExpression call) -> (Step next) -> new MapStep(call, next))
    .register(FLAT_MAP, (PsiMethodCallExpression call) -> (Step next) -> new FlatMapStep(call, next))
    .register(PEEK, (PsiMethodCallExpression call) -> (Step next) -> new PeekStep(call, next))
    .register(SORTED, (PsiMethodCallExpression call) -> (Step next) -> new SortedStep(call, next))
    .register(BOXED, (PsiMethodCallExpression call) -> (Step next) -> new BoxedStep(call, next))
    .register(STATE_FILTER, (PsiMethodCallExpression call) -> (Step next) -> new StateFilterStep(call, next));

  private static final CallMapper<Step> TERMINAL_STEP_MAPPER = new CallMapper<Step>()
    .register(FOR_TERMINAL, LambdaTerminalStep::new)
    .register(MATCH_TERMINAL, MatchTerminalStep::new)
    .register(SUM_TERMINAL, SumTerminalStep::new)
    .register(MIN_MAX_TERMINAL, MinMaxTerminalStep::new)
    .register(OPTIONAL_TERMINAL, OptionalTerminalStep::new);

  private static final Step NULL_TERMINAL_STEP = new Step(null, null, null) {
    @Override
    void before(CFGBuilder builder) {
      builder.flushFields();
    }

    @Override
    void iteration(CFGBuilder builder) {
      builder.pop();
    }

    @Override
    void pushResult(CFGBuilder builder) {
      builder.pushUnknown();
    }
  };

  static abstract class Step {
    final Step myNext;
    final PsiMethodCallExpression myCall;
    final PsiExpression myFunction;

    Step(PsiMethodCallExpression call, Step next, PsiExpression function) {
      myNext = next;
      myCall = call;
      myFunction = function;
    }

    void before(CFGBuilder builder) {
      if (myFunction != null) {
        builder.evaluateFunction(myFunction);
      }
      if (myNext != null) {
        myNext.before(builder);
      }
    }

    abstract void iteration(CFGBuilder builder);

    void pushResult(CFGBuilder builder) {
      if (myNext != null) {
        myNext.pushResult(builder);
      }
      else {
        builder.push(builder.getFactory()
                       .createTypeValue(myCall.getType(), DfaPsiUtil.getElementNullability(myCall.getType(), myCall.resolveMethod())));
      }
    }

    boolean expectNotNull() {
      return false;
    }
  }

  static class UnknownTerminalStep extends Step {
    UnknownTerminalStep(PsiMethodCallExpression call) {
      super(call, null, null);
    }

    @Override
    void before(CFGBuilder builder) {
      for (PsiExpression arg : myCall.getArgumentList().getExpressions()) {
        builder.pushExpression(arg).pop();
      }
      super.before(builder);
    }

    @Override
    void iteration(CFGBuilder builder) {
      // Stream variable is on stack
      builder.pop().flushFields();
    }
  }

  static abstract class TerminalStep extends Step {
    PsiVariable myResult;

    TerminalStep(@NotNull PsiMethodCallExpression call, PsiExpression function) {
      super(call, null, function);
    }

    @Override
    void before(CFGBuilder builder) {
      myResult = builder.createTempVariable(myCall.getType());
      builder.pushVariable(myResult)
        .chain(this::pushInitialValue)
        .assign()
        .pop()
        .chain(super::before);
    }

    protected abstract void pushInitialValue(CFGBuilder builder);

    @Override
    void pushResult(CFGBuilder builder) {
      builder.push(builder.getFactory().getVarFactory().createVariableValue(myResult, false));
    }
  }

  static class LambdaTerminalStep extends Step {
    LambdaTerminalStep(@NotNull PsiMethodCallExpression call) {
      super(call, null, call.getArgumentList().getExpressions()[0]);
    }

    @Override
    void iteration(CFGBuilder builder) {
      builder.invokeFunction(1, myFunction).pop();
    }
  }

  static class SumTerminalStep extends TerminalStep {
    SumTerminalStep(@NotNull PsiMethodCallExpression call) {
      super(call, null);
    }

    @Override
    protected void pushInitialValue(CFGBuilder builder) {
      PsiType type = myCall.getType();
      Object value = PsiTypesUtil.getDefaultValue(type);
      builder.push(builder.getFactory().getConstFactory().createFromValue(value, type, null));
    }

    @Override
    void iteration(CFGBuilder builder) {
      builder.pushVariable(myResult).pushUnknown().assign().splice(2);
    }
  }

  static class OptionalTerminalStep extends TerminalStep {
    OptionalTerminalStep(@NotNull PsiMethodCallExpression call) {
      super(call, ArrayUtil.getFirstElement(call.getArgumentList().getExpressions()));
    }

    @Override
    protected void pushInitialValue(CFGBuilder builder) {
      builder.push(builder.getFactory().getFactValue(DfaFactType.OPTIONAL_PRESENCE, false));
    }

    @Override
    void iteration(CFGBuilder builder) {
      if (myFunction != null) {
        builder.pushUnknown().invokeFunction(2, myFunction);
      }
      builder.pushVariable(myResult).push(builder.getFactory().getFactValue(DfaFactType.OPTIONAL_PRESENCE, true)).assign().splice(2);
    }
  }

  static class MinMaxTerminalStep extends TerminalStep {
    private final ComparatorModel myComparatorModel;

    MinMaxTerminalStep(@NotNull PsiMethodCallExpression call) {
      super(call, null);
      myComparatorModel = ComparatorModel.from(call.getArgumentList().getExpressions()[0]);
    }

    @Override
    protected void pushInitialValue(CFGBuilder builder) {
      builder.push(builder.getFactory().getFactValue(DfaFactType.OPTIONAL_PRESENCE, false));
    }

    @Override
    void before(CFGBuilder builder) {
      myComparatorModel.evaluate(builder);
      super.before(builder);
    }

    @Override
    void iteration(CFGBuilder builder) {
      myComparatorModel.invoke(builder);
      builder.pushVariable(myResult).push(builder.getFactory().getFactValue(DfaFactType.OPTIONAL_PRESENCE, true)).assign().pop();
    }

    @Override
    boolean expectNotNull() {
      return myComparatorModel.failsOnNull();
    }
  }

  static class MatchTerminalStep extends TerminalStep {
    MatchTerminalStep(@NotNull PsiMethodCallExpression call) {
      super(call, call.getArgumentList().getExpressions()[0]);
    }

    @Override
    protected void pushInitialValue(CFGBuilder builder) {
      builder.push(builder.getFactory().getBoolean(!"anyMatch".equals(myCall.getMethodExpression().getReferenceName())));
    }

    @Override
    void iteration(CFGBuilder builder) {
      builder.invokeFunction(1, myFunction)
        .ifConditionIs(!"allMatch".equals(myCall.getMethodExpression().getReferenceName()))
        .pushVariable(myResult)
        .push(builder.getFactory().getBoolean("anyMatch".equals(myCall.getMethodExpression().getReferenceName())))
        .assign()
        .pop()
        .endIf();
    }
  }

  static class FilterStep extends Step {
    FilterStep(@NotNull PsiMethodCallExpression call, Step next) {
      super(call, next, call.getArgumentList().getExpressions()[0]);
    }

    @Override
    void iteration(CFGBuilder builder) {
      builder
        .dup()
        .invokeFunction(1, myFunction)
        .ifConditionIs(true)
        .chain(myNext::iteration)
        .elseBranch()
        .pop()
        .endIf();
    }
  }

  static class MapStep extends Step {
    MapStep(@NotNull PsiMethodCallExpression call, Step next) {
      super(call, next, call.getArgumentList().getExpressions()[0]);
    }

    @Override
    void iteration(CFGBuilder builder) {
      builder
        .invokeFunction(1, myFunction, myNext.expectNotNull() ? Nullness.NOT_NULL : Nullness.UNKNOWN)
        .assignTo(builder.createTempVariable(StreamApiUtil.getStreamElementType(myCall.getType())))
        .chain(myNext::iteration);
    }
  }

  static class FlatMapStep extends Step {
    private final Step myChain;
    private final PsiParameter myParameter;
    private final PsiExpression myStreamSource;

    FlatMapStep(@NotNull PsiMethodCallExpression call, Step next) {
      super(call, next, null);
      // Try to inline smoothly .flatMap(x -> stream().call().chain())
      PsiLambdaExpression lambda =
        ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(myCall.getArgumentList().getExpressions()[0]), PsiLambdaExpression.class);
      Step chain = null;
      PsiParameter parameter = null;
      PsiExpression streamSource = null;
      if (lambda != null) {
        parameter = ArrayUtil.getFirstElement(lambda.getParameterList().getParameters());
        if (parameter != null) {
          PsiExpression body = PsiUtil.skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(lambda.getBody()));
          if (body != null) {
            streamSource = body;
            chain = next;
            if (body instanceof PsiMethodCallExpression) {
              Step filteredNext = new Step(call, next, null) {
                @Override
                void before(CFGBuilder builder) {
                  // skip following steps: their before behavior is added before the main loop
                }

                @Override
                void iteration(CFGBuilder builder) {
                  myNext.iteration(builder);
                }
              };
              chain = buildChain((PsiMethodCallExpression)body, filteredNext);
              if (chain != filteredNext) {
                streamSource = chain.myCall.getMethodExpression().getQualifierExpression();
              } else {
                streamSource = body;
              }
            }
          }
        }
      }
      myStreamSource = streamSource;
      myChain = chain;
      myParameter = parameter;
    }

    @Override
    void before(CFGBuilder builder) {
      if (myStreamSource == null) {
        PsiExpression arg = myCall.getArgumentList().getExpressions()[0];
        builder.pushExpression(arg).checkNotNull(arg, NullabilityProblemKind.passingNullableToNotNullParameter).pop();
      }
      super.before(builder);
    }

    @Override
    void iteration(CFGBuilder builder) {
      if (myStreamSource != null) {
        builder.assignTo(myParameter).pop();
        buildStreamCFG(builder, myChain, myStreamSource);
      } else {
        PsiType outType = StreamApiUtil.getStreamElementType(myCall.getType());
        builder.pop()
          .pushUnknown()
          .ifConditionIs(true)
          .doWhile()
          .push(builder.getFactory().createTypeValue(outType, Nullness.UNKNOWN))
          .chain(myNext::iteration)
          .endWhileUnknown()
          .endIf();
      }
    }
  }

  static class PeekStep extends Step {
    PeekStep(@NotNull PsiMethodCallExpression call, Step next) {
      super(call, next, call.getArgumentList().getExpressions()[0]);
    }

    @Override
    void iteration(CFGBuilder builder) {
      builder
        .dup()
        .invokeFunction(1, myFunction)
        .pop()
        .chain(myNext::iteration);
    }

    @Override
    boolean expectNotNull() {
      return myNext.expectNotNull();
    }
  }

  static class StateFilterStep extends Step {
    StateFilterStep(@NotNull PsiMethodCallExpression call, Step next) {
      super(call, next, null);
    }

    @Override
    void before(CFGBuilder builder) {
      for (PsiExpression arg : myCall.getArgumentList().getExpressions()) {
        builder.pushExpression(arg).pop();
      }
      super.before(builder);
    }

    @Override
    void iteration(CFGBuilder builder) {
      builder
        .pushUnknown()
        .ifConditionIs(true)
        .chain(myNext::iteration)
        .elseBranch()
        .pop()
        .endIf();
    }
  }

  static class SortedStep extends Step {
    private final ComparatorModel myComparatorModel;

    SortedStep(@NotNull PsiMethodCallExpression call, Step next) {
      super(call, next, null);
      myComparatorModel = ComparatorModel.from(ArrayUtil.getFirstElement(myCall.getArgumentList().getExpressions()));
    }

    @Override
    void before(CFGBuilder builder) {
      myComparatorModel.evaluate(builder);
      super.before(builder);
    }

    @Override
    void iteration(CFGBuilder builder) {
      builder.dup();
      myComparatorModel.invoke(builder);
      myNext.iteration(builder);
    }

    @Override
    boolean expectNotNull() {
      return myComparatorModel.failsOnNull() || myNext.expectNotNull();
    }
  }

  static class BoxedStep extends Step {
    BoxedStep(@NotNull PsiMethodCallExpression call, Step next) {
      super(call, next, null);
    }

    @Override
    void iteration(CFGBuilder builder) {
      PsiType outType = StreamApiUtil.getStreamElementType(myCall.getType());
      PsiPrimitiveType primitiveType = PsiPrimitiveType.getUnboxedType(outType);
      if (primitiveType != null) {
        builder.boxUnbox(myCall, primitiveType, outType).assignTo(builder.createTempVariable(outType));
      }
      myNext.iteration(builder);
    }
  }

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    if (TERMINAL_CALL.test(call)) {
      return inlineCompleteStream(builder, call);
    }
    else {
      return inlinePartialStream(builder, call);
    }
  }

  private static boolean inlinePartialStream(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    Step firstStep = buildChain(call, NULL_TERMINAL_STEP);
    if (firstStep == NULL_TERMINAL_STEP) {
      return false;
    }
    PsiExpression originalQualifier = firstStep.myCall.getMethodExpression().getQualifierExpression();
    if (originalQualifier == null) return false;
    builder.pushUnknown()
      .ifConditionIs(true)
      .chain(b -> buildStreamCFG(b, firstStep, originalQualifier))
      .endIf()
      .push(builder.getFactory().createTypeValue(call.getType(), Nullness.NOT_NULL));
    return true;
  }

  private static boolean inlineCompleteStream(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
    Step terminalStep = createTerminalStep(call);
    Step firstStep = buildChain(qualifierCall, terminalStep);
    PsiExpression originalQualifier = firstStep.myCall.getMethodExpression().getQualifierExpression();
    if (originalQualifier == null) return false;
    buildStreamCFG(builder, firstStep, originalQualifier);
    firstStep.pushResult(builder);
    return true;
  }

  static void buildStreamCFG(CFGBuilder builder, Step firstStep, PsiExpression originalQualifier) {
    PsiType inType = StreamApiUtil.getStreamElementType(originalQualifier.getType());
    PsiMethodCallExpression sourceCall = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(originalQualifier), PsiMethodCallExpression.class);
    if(STREAM_GENERATE.test(sourceCall)) {
      PsiExpression fn = sourceCall.getArgumentList().getExpressions()[0];
      builder
        .evaluateFunction(fn)
        .chain(firstStep::before)
        .doWhile()
        .pushVariable(builder.createTempVariable(inType))
        .invokeFunction(0, fn)
        .assign()
        .chain(firstStep::iteration)
        .endWhileUnknown();
      return;
    }
    boolean empty = STREAM_EMPTY.test(sourceCall);
    boolean array = ARRAYS_STREAM.test(sourceCall);
    boolean single = STREAM_OF.test(sourceCall);
    if (STREAM_OF_ARRAY.test(sourceCall)) {
      PsiExpression[] args = sourceCall.getArgumentList().getExpressions();
      if(args.length == 0) {
        empty = true;
      } else if(args.length == 1) {
        if(MethodCallUtils.isVarArgCall(sourceCall)) {
          single = true;
        } else {
          array = true;
        }
      } else {
        builder
          .pushExpression(originalQualifier)
          .pop()
          .chain(firstStep::before)
          .chain(b -> makeMainLoop(b, firstStep, inType));
        return;
      }
    }
    if (empty) {
      builder.chain(firstStep::before); // skip loop at all
      return;
    }
    if (single) {
      // exactly single element
      builder
        .pushExpression(sourceCall.getArgumentList().getExpressions()[0])
        .chain(firstStep::before)
        .chain(firstStep::iteration);
      return;
    }
    if (array) {
      PsiExpression qualifierExpression = sourceCall.getArgumentList().getExpressions()[0];
      DfaValue qualifierValue = builder.getFactory().createValue(qualifierExpression);
      if (qualifierValue != null) {
        builder.pushExpression(qualifierExpression)
          .chain(firstStep::before)
          .checkNotNull(qualifierExpression, NullabilityProblemKind.passingNullableToNotNullParameter)
          .pop()
          .push(SpecialField.ARRAY_LENGTH.createValue(builder.getFactory(), qualifierValue))
          .push(builder.getFactory().getInt(0))
          .ifCondition(JavaTokenType.GT)
          .chain(b -> makeMainLoop(b, firstStep, inType))
          .endIf();
        return;
      }
    }
    if (COLLECTION_STREAM.test(sourceCall)) {
      PsiExpression qualifierExpression = sourceCall.getMethodExpression().getQualifierExpression();
      DfaValue qualifierValue = builder.getFactory().createValue(qualifierExpression);
      if (qualifierValue != null) {
        builder.pushExpression(qualifierExpression)
          .chain(firstStep::before)
          .checkNotNull(sourceCall, NullabilityProblemKind.callNPE)
          .pop()
          .push(SpecialField.COLLECTION_SIZE.createValue(builder.getFactory(), qualifierValue))
          .push(builder.getFactory().getInt(0))
          .ifCondition(JavaTokenType.GT)
          .chain(b -> makeMainLoop(b, firstStep, inType))
          .endIf();
        return;
      }
    }
    builder
      .pushExpression(originalQualifier)
      .checkNotNull(firstStep.myCall, NullabilityProblemKind.callNPE)
      .pop()
      .chain(firstStep::before)
      .pushUnknown()
      .ifConditionIs(true)
      .chain(b -> makeMainLoop(b, firstStep, inType))
      .endIf();
  }

  private static void makeMainLoop(CFGBuilder builder, Step firstStep, PsiType inType) {
    builder.doWhile()
      .pushVariable(builder.createTempVariable(inType))
      .push(builder.getFactory().createTypeValue(inType, DfaPsiUtil.getTypeNullability(inType)))
      .assign()
      .chain(firstStep::iteration)
      .endWhileUnknown();
  }

  static Step buildChain(PsiMethodCallExpression qualifierCall, Step terminalStep) {
    Step curStep = terminalStep;
    while (qualifierCall != null) {
      if (!SKIP_STEP.test(qualifierCall)) {
        Step nextStep = createIntermediateStep(curStep, qualifierCall);
        if (nextStep == null) break;
        curStep = nextStep;
      }
      qualifierCall = MethodCallUtils.getQualifierMethodCall(qualifierCall);
    }
    return curStep;
  }

  private static Step createIntermediateStep(Step nextStep, PsiMethodCallExpression call) {
    UnaryOperator<Step> stepFactory = INTERMEDIATE_STEP_MAPPER.mapFirst(call);
    if (stepFactory == null) return null;
    return stepFactory.apply(nextStep);
  }

  private static Step createTerminalStep(PsiMethodCallExpression call) {
    Step step = TERMINAL_STEP_MAPPER.mapFirst(call);
    return step == null ? new UnknownTerminalStep(call) : step;
  }
}
