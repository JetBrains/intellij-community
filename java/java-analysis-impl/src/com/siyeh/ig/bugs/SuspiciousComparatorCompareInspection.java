// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ThisDescriptor;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.types.DfLongType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class SuspiciousComparatorCompareInspection extends BaseInspection {

  @NotNull
  @Override
  public String getShortName() {
    return "ComparatorMethodParameterNotUsed";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return (String)infos[0];
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousComparatorCompareVisitor();
  }

  private static class SuspiciousComparatorCompareVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (MethodUtils.isComparatorCompare(method) && !ControlFlowUtils.methodAlwaysThrowsException(method)) {
        check(method, false);
      }
      if (MethodUtils.isCompareTo(method) && !ControlFlowUtils.methodAlwaysThrowsException(method)) {
        check(method, true);
      }
    }

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression lambda) {
      super.visitLambdaExpression(lambda);
      final PsiClass functionalInterface = LambdaUtil.resolveFunctionalInterfaceClass(lambda);
      if (functionalInterface == null || !CommonClassNames.JAVA_UTIL_COMPARATOR.equals(functionalInterface.getQualifiedName()) ||
          ControlFlowUtils.lambdaExpressionAlwaysThrowsException(lambda)) {
        return;
      }
      check(lambda, false);
    }

    private void check(PsiParameterListOwner owner, boolean compareTo) {
      PsiParameterList parameterList = owner.getParameterList();
      PsiElement body = owner.getBody();
      int expectedParameters = compareTo ? 1 : 2;
      if (body == null || parameterList.getParametersCount() != expectedParameters) return;
      // comparator like "(a, b) -> 0" fulfills the comparator contract, so no need to warn its parameters are not used
      if (body instanceof PsiExpression && ExpressionUtils.isZero((PsiExpression)body)) return;
      if (body instanceof PsiCodeBlock) {
        PsiStatement statement = ControlFlowUtils.getOnlyStatementInBlock((PsiCodeBlock)body);
        if (statement instanceof PsiReturnStatement && ExpressionUtils.isZero(((PsiReturnStatement)statement).getReturnValue())) return;
      }
      PsiMethodCallExpression soleCall = ObjectUtils.tryCast(LambdaUtil.extractSingleExpressionFromBody(body), PsiMethodCallExpression.class);
      if (soleCall != null) {
        MethodContract contract = ContainerUtil.getOnlyItem(JavaMethodContractUtil.getMethodCallContracts(soleCall));
        if (contract != null && contract.isTrivial() && contract.getReturnValue().isFail()) return;
      }
      PsiParameter[] parameters = parameterList.getParameters();
      checkParameterList(parameters, body, compareTo ? "compareTo" : "compare");
      checkReturnValueSanity(owner instanceof PsiMethod method ? method.getNameIdentifier() : parameterList, body);
      checkReflexivity(owner, parameters, body);
      checkReturnMinValue(body);
    }

    private void checkReturnMinValue(PsiElement body) {
      StreamEx<PsiExpression> stream;
      if (body instanceof PsiExpression expression) {
        stream = StreamEx.of(expression);
      } else if (body instanceof PsiCodeBlock block) {
        stream = StreamEx.of(PsiUtil.findReturnStatements(block))
          .map(PsiReturnStatement::getReturnValue)
          .nonNull();
      }
      else {
        return;
      }
      stream.flatMap(ExpressionUtils::nonStructuralChildren).forEach(expr -> {
        if (ExpressionUtils.computeConstantExpression(expr) instanceof Integer i && i == Integer.MIN_VALUE) {
          registerError(expr, InspectionGadgetsBundle.message("suspicious.comparator.compare.descriptor.min.value"));
        }
      });
    }

    private void checkReturnValueSanity(PsiElement anchor, PsiElement body) {
      LongRangeSet range;
      if (body instanceof PsiExpression expression) {
        range = DfLongType.extractRange(CommonDataflow.getDfType(expression));
      } else if (body instanceof PsiCodeBlock block) {
        range = StreamEx.of(PsiUtil.findReturnStatements(block))
          .map(PsiReturnStatement::getReturnValue)
          .nonNull()
          .map(CommonDataflow::getDfType)
          .map(DfLongType::extractRange)
          .reduce(LongRangeSet.empty(), LongRangeSet::join);
      } else {
        return;
      }
      if (range.isEmpty() || range.equals(LongRangeSet.point(0))) return;
      if (range.min() >= 0) {
        registerError(anchor, InspectionGadgetsBundle.message("suspicious.comparator.compare.descriptor.non.negative"));
      }
      else if (range.max() <= 0) {
        registerError(anchor, InspectionGadgetsBundle.message("suspicious.comparator.compare.descriptor.non.positive"));
      }
    }

    private void checkParameterList(PsiParameter[] parameters, PsiElement context, String methodName) {
      final ParameterAccessVisitor visitor = new ParameterAccessVisitor(parameters);
      context.accept(visitor);
      for (PsiParameter unusedParameter : visitor.getUnusedParameters()) {
        registerVariableError(unusedParameter, InspectionGadgetsBundle.message(
          "suspicious.comparator.compare.descriptor.parameter.not.used", methodName));
      }
    }

    private void checkReflexivity(PsiParameterListOwner owner, PsiParameter[] parameters, PsiElement body) {
      DfaValueFactory factory = new DfaValueFactory(owner.getProject());
      ControlFlow flow = ControlFlowAnalyzer.buildFlow(body, factory, true);
      if (flow == null) return;
      DfaMemoryState state = new JvmDfaMemoryStateImpl(factory);
      DfaVariableValue var1 = PlainDescriptor.createVariableValue(factory, parameters[0]);
      DfaVariableValue var2;
      if (parameters.length == 2) {
        // compare()
        var2 = PlainDescriptor.createVariableValue(factory, parameters[1]);
      } else {
        // compareTo()
        assert owner instanceof PsiMethod;
        var2 = ThisDescriptor.createThisValue(factory, ((PsiMethod)owner).getContainingClass());
      }
      state.applyCondition(var1.eq(var2));
      var interceptor = new ComparatorListener(owner);
      if (new StandardDataFlowInterpreter(flow, interceptor).interpret(state) != RunnerResult.OK) return;
      if (interceptor.myRange.contains(0) || interceptor.myContexts.isEmpty()) return;
      PsiElement context = null;
      if (interceptor.myContexts.size() == 1) {
        context = interceptor.myContexts.iterator().next();
      }
      else {
        PsiElement commonParent = PsiTreeUtil.findCommonParent(interceptor.myContexts.toArray(PsiElement.EMPTY_ARRAY));
        if (commonParent instanceof PsiExpression) {
          context = commonParent;
        } else {
          PsiParameterListOwner parent = PsiTreeUtil.getParentOfType(body, PsiMethod.class, PsiLambdaExpression.class);
          if (parent instanceof PsiMethod) {
            context = ((PsiMethod)parent).getNameIdentifier();
          }
          else if (parent instanceof PsiLambdaExpression) {
            context = parent.getParameterList();
          }
        }
      }
      registerError(context != null ? context : body,
                    InspectionGadgetsBundle.message("suspicious.comparator.compare.descriptor.non.reflexive"));
    }

    private static final class ComparatorListener implements JavaDfaListener {
      private final PsiParameterListOwner myOwner;
      private final Set<PsiElement> myContexts = new HashSet<>();
      LongRangeSet myRange = LongRangeSet.empty();

      private ComparatorListener(PsiParameterListOwner owner) {
        myOwner = owner;
      }

      @Override
      public void beforeValueReturn(@NotNull DfaValue value,
                                    @Nullable PsiExpression expression,
                                    @NotNull PsiElement owner,
                                    @NotNull DfaMemoryState state) {
        if (owner != myOwner || expression == null) return;
        myContexts.add(expression);
        myRange = myRange.join(DfIntType.extractRange(state.getDfType(value)));
      }
    }

    private static final class ParameterAccessVisitor extends JavaRecursiveElementWalkingVisitor {

      private final Set<PsiParameter> parameters;

      private ParameterAccessVisitor(PsiParameter @NotNull [] parameters) {
        this.parameters = ContainerUtil.newHashSet(parameters);
      }

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (expression.getQualifierExpression() != null) {
          // optimization
          // references to parameters are never qualified
          return;
        }
        final PsiElement target = expression.resolve();
        if (!(target instanceof PsiParameter parameter)) {
          return;
        }
        parameters.remove(parameter);
        if (parameters.isEmpty()) {
          stopWalking();
        }
      }

      private Collection<PsiParameter> getUnusedParameters() {
        return Collections.unmodifiableSet(parameters);
      }
    }
  }
}