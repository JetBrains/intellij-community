// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.jvm.problems.ContractFailureProblem;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.RemoveRedundantPolyadicOperandFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.ReorderingUtils;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ConditionCoveredByFurtherConditionInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(ConditionCoveredByFurtherConditionInspection.class);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new ConditionCoveredByFurtherConditionVisitor(holder);
  }

  private static class ConditionCoveredByFurtherConditionVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    ConditionCoveredByFurtherConditionVisitor(ProblemsHolder holder) {myHolder = holder;}

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      IElementType type = expression.getOperationTokenType();
      if (type.equals(JavaTokenType.ANDAND)) {
        processConditionChain(expression, true);
      }
      else if (type.equals(JavaTokenType.OROR)) {
        processConditionChain(expression, false);
      }
    }

    private void processConditionChain(PsiPolyadicExpression expression, boolean and) {
      StreamEx.of(expression.getOperands())
        .map(operand -> isAllowed(operand) ? operand : null)
        .groupRuns((o1, o2) -> o1 != null && o2 != null)
        .remove(list -> list.size() == 1)
        .forEach(operands -> process(expression, operands, and));
    }

    private static boolean isAllowed(PsiExpression expression) {
      return ReorderingUtils.isSideEffectFree(expression, true);
    }

    private void process(PsiPolyadicExpression context, List<PsiExpression> operands, boolean and) {
      int[] indices = getRedundantOperandIndices(context, operands, and);
      for (int index : indices) {
        List<PsiExpression> dependencies = operands.subList(index + 1, operands.size());
        PsiExpression operand = operands.get(index);
        dependencies = minimizeDependencies(context, operand, and, dependencies);
        if (dependencies.isEmpty()) continue;
        PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(operand);
        if (stripped == null) continue;
        DfaValueFactory factory = new DfaValueFactory(myHolder.getProject());
        Set<DfaVariableValue> notNullValues = getNotNullValues(factory, operand);
        if (notNullValues == null || !notNullValues.equals(getNotNullValues(factory, operands.get(index + 1)))) continue;
        String operandText = PsiExpressionTrimRenderer.render(stripped);
        String description =
          InspectionGadgetsBundle.message("inspection.condition.covered.by.further.condition.descr",
                                          operandText, dependencies.size(), PsiExpressionTrimRenderer
                                            .render(Objects.requireNonNull(PsiUtil.skipParenthesizedExprDown(dependencies.get(0)))));
        myHolder.registerProblem(operand, description, new RemoveRedundantPolyadicOperandFix(operandText));
      }
    }

    private static List<PsiExpression> minimizeDependencies(PsiPolyadicExpression context,
                                                            PsiExpression operand,
                                                            boolean and, List<PsiExpression> dependencies) {
      if (dependencies.isEmpty() || getRedundantOperandIndices(context, Collections.singletonList(operand), and).length != 0) {
        // Does not actually depend on dependencies
        return Collections.emptyList();
      }
      if (dependencies.size() == 1) return dependencies;
      for (PsiExpression dependency : dependencies) {
        if (ArrayUtil.indexOf(getRedundantOperandIndices(context, Arrays.asList(operand, dependency), and), 0) != -1) {
          return Collections.singletonList(dependency);
        }
      }
      return dependencies;
    }

    private static int[] getRedundantOperandIndices(PsiPolyadicExpression context, List<PsiExpression> operands, boolean and) {
      assert !operands.isEmpty();
      if (operands.size() == 1) {
        Object value = CommonDataflow.computeValue(operands.get(0));
        return Boolean.valueOf(and).equals(value) ? new int[]{0} : ArrayUtilRt.EMPTY_INT_ARRAY;
      }
      String text = StreamEx.ofReversed(operands)
        .map(expression -> ParenthesesUtils.getText(expression, PsiPrecedenceUtil.AND_PRECEDENCE)).joining(and ? " && " : " || ");
      PsiExpression expression = JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText(text, context);
      if (!(expression instanceof PsiPolyadicExpression expressionToAnalyze)) {
        LOG.error("Unexpected expression type: " + expression.getClass().getName(), new Attachment("reversed.txt", text));
        return ArrayUtilRt.EMPTY_INT_ARRAY;
      }
      List<PsiExpression> reversedOperands = Arrays.asList(expressionToAnalyze.getOperands());
      Map<PsiExpression, ThreeState> values = computeOperandValues(expressionToAnalyze);
      return StreamEx.ofKeys(values, ThreeState.fromBoolean(and)::equals)
        .mapToInt(operand -> IntStreamEx.ofIndices(reversedOperands, op -> PsiTreeUtil.isAncestor(op, operand, false))
          .findFirst().orElse(0))
        .map(index -> operands.size() - 1 - index)
        .toArray();
    }
  }

  @NotNull
  private static Map<PsiExpression, ThreeState> computeOperandValues(PsiPolyadicExpression expressionToAnalyze) {
    DfaValueFactory factory = new DfaValueFactory(expressionToAnalyze.getProject());
    ControlFlow flow = ControlFlowAnalyzer.buildFlow(expressionToAnalyze, factory, true);
    if (flow == null) return Map.of();
    var listener = new JavaDfaListener() {
      final Map<PsiExpression, ThreeState> values = new HashMap<>();
      final Set<DfaVariableValue> myOkNullChecks = new HashSet<>();
      DataFlowInterpreter myInterpreter;

      @Override
      public void beforeExpressionPush(@NotNull DfaValue value,
                                       @NotNull PsiExpression expression,
                                       @NotNull DfaMemoryState state) {
        if (value instanceof DfaVariableValue && myOkNullChecks.contains(value)) {
          DfType type = state.getDfType(value);
          if (type instanceof DfReferenceType && ((DfReferenceType)type).getNullability() == DfaNullability.NOT_NULL) {
            type = type instanceof DfConstantType ? DfType.TOP : ((DfReferenceType)type).dropNullability();
            ((DfaMemoryStateImpl)state).recordVariableType((DfaVariableValue)value, type);
          }
        }
        if (PsiUtil.skipParenthesizedExprUp(expression.getParent()) != expressionToAnalyze) return;
        ThreeState old = values.get(expression);
        if (old == ThreeState.UNSURE) return;
        ThreeState result = ThreeState.UNSURE;
        Boolean bool = state.getDfType(value).getConstantOfType(Boolean.class);
        if (bool != null) {
          result = ThreeState.fromBoolean(bool);
        }
        values.put(expression, old == null || old == result ? result : ThreeState.UNSURE);
      }

      @Override
      public void onCondition(@NotNull UnsatisfiedConditionProblem problem,
                              @NotNull DfaValue value,
                              @NotNull ThreeState failed,
                              @NotNull DfaMemoryState state) {
        if (failed != ThreeState.NO && problem instanceof ContractFailureProblem) {
          myInterpreter.cancel();
        }
        else if (problem instanceof NullabilityProblemKind.NullabilityProblem<?> && value instanceof DfaVariableValue) {
          myOkNullChecks.add((DfaVariableValue)value);
        }
      }
    };
    var interpreter = new StandardDataFlowInterpreter(flow, listener);
    listener.myInterpreter = interpreter;
    RunnerResult result = interpreter.interpret(createMemoryState(factory));
    return result == RunnerResult.OK ? listener.values : Collections.emptyMap();
  }

  private static @Nullable Set<DfaVariableValue> getNotNullValues(@NotNull DfaValueFactory factory, @Nullable PsiExpression expression) {
    if (expression == null) return null;
    ControlFlow flow = ControlFlowAnalyzer.buildFlow(expression, factory, true);
    if (flow == null) return null;
    var listener = new DfaListener() {
      final Map<DfaVariableValue, Boolean> result = new HashMap<>();

      @Override
      public void onCondition(@NotNull UnsatisfiedConditionProblem problem,
                              @NotNull DfaValue value,
                              @NotNull ThreeState failed,
                              @NotNull DfaMemoryState state) {
        if (problem instanceof NullabilityProblemKind.NullabilityProblem &&
            ((NullabilityProblemKind.NullabilityProblem<?>)problem).thrownException() != null &&
            value instanceof DfaVariableValue) {
          result.merge((DfaVariableValue)value, failed != ThreeState.NO, Boolean::logicalAnd);
        }
      }

      @Override
      public void beforePush(@NotNull DfaValue @NotNull [] args,
                             @NotNull DfaValue value,
                             @NotNull DfaAnchor anchor,
                             @NotNull DfaMemoryState state) {
        if (anchor instanceof JavaExpressionAnchor && ((JavaExpressionAnchor)anchor).getExpression() == expression) {
          Set<DfaVariableValue> vars = result.keySet();
          StreamEx.of(vars)
            .filter(v -> DfaNullability.fromDfType(state.getDfType(v)) != DfaNullability.NOT_NULL)
            .toSetAndThen(vars::removeAll);
        }

      }
    };
    var interpreter = new StandardDataFlowInterpreter(flow, listener);
    if (interpreter.interpret(new JvmDfaMemoryStateImpl(factory)) != RunnerResult.OK) return null;
    return StreamEx.ofKeys(listener.result, x -> x).toSet();
  }

  @NotNull
  private static DfaMemoryState createMemoryState(DfaValueFactory factory) {
    DfaMemoryState state = new JvmDfaMemoryStateImpl(factory);
    List<DfaVariableValue> vars = StreamEx.of(factory.getValues())
      .select(DfaVariableValue.class)
      .filter(var -> {
        if (!(var.getInherentType() instanceof DfReferenceType) ||
            ((DfReferenceType)var.getInherentType()).getNullability() == DfaNullability.UNKNOWN) {
          return false;
        }
        PsiVariable psi = ObjectUtils.tryCast(var.getPsiVariable(), PsiVariable.class);
        if (psi instanceof PsiPatternVariable) return true;
        if (psi instanceof PsiLocalVariable || psi instanceof PsiParameter) {
          PsiElement block = PsiUtil.getVariableCodeBlock(psi, null);
          return block == null || !HighlightControlFlowUtil.isEffectivelyFinal(psi, block, null);
        }
        return true;
      })
      .toList();
    for (DfaVariableValue var : vars) {
      state.setVarValue(var, factory.fromDfType(((DfReferenceType)var.getInherentType()).dropNullability()));
    }
    return state;
  }
}
