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

import com.intellij.codeInspection.dataFlow.CFGBuilder;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.StreamApiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_STREAM_STREAM;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public class StreamChainInliner implements CallInliner {
  private static final String[] TERMINALS =
    {"count", "sum", "summaryStatistics", "reduce", "collect", "findFirst", "findAny", "anyMatch", "allMatch", "noneMatch", "toArray",
      "average", "forEach", "forEachOrdered", "min", "max", "toList", "toSet"};
  private static final CallMatcher TERMINAL_CALL = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, TERMINALS);

  private static final CallMatcher LAMBDA_TERMINAL = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "anyMatch", "allMatch",
                                                                  "noneMatch", "forEach", "forEachOrdered").parameterCount(1);

  private static final CallMatcher SKIP_STEP =
    instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "unordered", "parallel", "sequential", "sorted").parameterCount(0);
  private static final CallMatcher SORTED = instanceCall(JAVA_UTIL_STREAM_STREAM, "sorted").parameterCount(1);
  private static final CallMatcher FILTER = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "filter").parameterCount(1);
  private static final CallMatcher STATE_FILTER = anyOf(instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "distinct").parameterCount(0),
                                                        instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "skip", "limit").parameterCount(1));
  private static final CallMatcher BOXED = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "boxed").parameterCount(0);
  private static final CallMatcher MAP =
    instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "map", "mapToInt", "mapToLong", "mapToDouble", "mapToObj").parameterCount(1);
  private static final CallMatcher FLAT_MAP =
    instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "flatMap", "flatMapToInt", "flatMapToLong", "flatMapToDouble").parameterCount(1);
  private static final CallMatcher PEEK = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "peek").parameterCount(1);

  private static final CallMapper<UnaryOperator<Step>> INTERMEDIATE_STEP_MAPPER = new CallMapper<UnaryOperator<Step>>()
    .register(FILTER, (PsiMethodCallExpression call) -> (Step next) -> new FilterStep(call, next))
    .register(MAP, (PsiMethodCallExpression call) -> (Step next) -> new MapStep(call, next))
    .register(FLAT_MAP, (PsiMethodCallExpression call) -> (Step next) -> new FlatMapStep(call, next))
    .register(PEEK, (PsiMethodCallExpression call) -> (Step next) -> new PeekStep(call, next))
    .register(SORTED, (PsiMethodCallExpression call) -> (Step next) -> new SortedStep(call, next))
    .register(BOXED, (PsiMethodCallExpression call) -> (Step next) -> new BoxedStep(call, next))
    .register(STATE_FILTER, (PsiMethodCallExpression call) -> (Step next) -> new StateFilterStep(call, next));

  static abstract class Step {
    final Step myNext;
    final @NotNull PsiMethodCallExpression myCall;
    final PsiExpression myFunction;

    Step(@NotNull PsiMethodCallExpression call, Step next, PsiExpression function) {
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

  static class LambdaTerminalStep extends Step {
    LambdaTerminalStep(@NotNull PsiMethodCallExpression call) {
      super(call, null, call.getArgumentList().getExpressions()[0]);
    }

    @Override
    void iteration(CFGBuilder builder) {
      builder.invokeFunction(1, myFunction).pop();
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
        .invokeFunction(1, myFunction)
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
              chain = buildChain((PsiMethodCallExpression)body, next);
              if (chain != next) {
                streamSource = chain.myCall.getMethodExpression().getQualifierExpression();
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
        builder.pushExpression(arg).checkNotNull(arg).pop();
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
          .doWhile()
          .push(builder.getFactory().createTypeValue(outType, Nullness.UNKNOWN))
          .chain(myNext::iteration)
          .endWhileUnknown();
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

  // Currently sorted is just a no-op as DFA results does not depend on sort order.
  // In future we could check the comparator implementation
  // (e.g. warn if stream can contain nulls, but comparator is not null-friendly)
  static class SortedStep extends Step {
    SortedStep(@NotNull PsiMethodCallExpression call, Step next) {
      super(call, next, null);
    }

    @Override
    void before(CFGBuilder builder) {
      builder.pushExpression(myCall.getArgumentList().getExpressions()[0]).pop();
      super.before(builder);
    }

    @Override
    void iteration(CFGBuilder builder) {
      myNext.iteration(builder);
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
    if (!TERMINAL_CALL.test(call)) {
      return false;
    }
    PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
    Step terminalStep = createTerminalStep(call);
    Step firstStep = buildChain(qualifierCall, terminalStep);
    if (firstStep == terminalStep) {
      // Do not handle specially case when only terminal operation is known: at least one intermediate op should be known as well
      return false;
    }
    PsiExpression originalQualifier = firstStep.myCall.getMethodExpression().getQualifierExpression();
    if (originalQualifier == null) return false;
    buildStreamCFG(builder, firstStep, originalQualifier);
    builder.push(
      builder.getFactory().createTypeValue(call.getType(), DfaPsiUtil.getElementNullability(call.getType(), call.resolveMethod())));
    return true;
  }

  static void buildStreamCFG(CFGBuilder builder, Step firstStep, PsiExpression originalQualifier) {
    PsiReferenceExpression firstCall = firstStep.myCall.getMethodExpression();
    PsiType inType = StreamApiUtil.getStreamElementType(originalQualifier.getType());
    builder
      .pushExpression(originalQualifier)
      .dereferenceCheck(firstCall)
      .pop()
      .chain(firstStep::before)
      .doWhile()
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
    if (LAMBDA_TERMINAL.test(call)) {
      return new LambdaTerminalStep(call);
    }
    return new UnknownTerminalStep(call);
  }
}
