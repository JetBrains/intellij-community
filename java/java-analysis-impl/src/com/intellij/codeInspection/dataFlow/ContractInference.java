/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.*;

/**
 * @author peter
 */
public class ContractInference {
  public static final int MAX_CONTRACT_COUNT = 10;

  @NotNull
  public static List<MethodContract> inferContracts(@NotNull final PsiMethod method) {
    if (!InferenceFromSourceUtil.shouldInferFromSource(method)) {
      return Collections.emptyList();
    }
    
    return CachedValuesManager.getCachedValue(method, () -> {
      List<MethodContract> result = RecursionManager.doPreventingRecursion(method, true, () ->
        new ContractInferenceInterpreter(method).inferContracts());
      if (result == null) result = Collections.emptyList();
      return CachedValueProvider.Result.create(result, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }
}

class ContractInferenceInterpreter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.ContractInferenceInterpreter");
  private final PsiMethod myMethod;
  private final ValueConstraint[] myEmptyConstraints;

  public ContractInferenceInterpreter(PsiMethod method) {
    myMethod = method;
    myEmptyConstraints = MethodContract.createConstraintArray(myMethod.getParameterList().getParametersCount());
  }

  List<MethodContract> inferContracts() {
    List<MethodContract> contracts = ContainerUtil.concat(doInferContracts(), c -> c.toContracts(myMethod));
    if (contracts.isEmpty()) return Collections.emptyList();
    
    final PsiType returnType = myMethod.getReturnType();
    if (returnType != null && !(returnType instanceof PsiPrimitiveType)) {
      contracts = boxReturnValues(contracts);
    }
    List<MethodContract> compatible = ContainerUtil.filter(contracts, contract -> {
      if ((contract.returnValue == NOT_NULL_VALUE || contract.returnValue == NULL_VALUE) &&
          NullableNotNullManager.getInstance(myMethod.getProject()).isNotNull(myMethod, false)) {
        return false;
      }
      return InferenceFromSourceUtil.isReturnTypeCompatible(returnType, contract.returnValue);
    });
    if (compatible.size() > ContractInference.MAX_CONTRACT_COUNT) {
      LOG.debug("Too many contracts for " + PsiUtil.getMemberQualifiedName(myMethod) + ", shrinking the list");
      return compatible.subList(0, ContractInference.MAX_CONTRACT_COUNT);
    }
    return compatible;
  }

  @NotNull
  private static List<MethodContract> boxReturnValues(List<MethodContract> contracts) {
    return ContainerUtil.mapNotNull(contracts, contract -> {
      if (contract.returnValue == FALSE_VALUE || contract.returnValue == TRUE_VALUE) {
        return new MethodContract(contract.arguments, NOT_NULL_VALUE);
      }
      return contract;
    });
  }

  private List<PreContract> doInferContracts() {
    PsiCodeBlock body = myMethod.getBody();
    PsiStatement[] statements = body == null ? PsiStatement.EMPTY_ARRAY : body.getStatements();
    if (statements.length == 0) return Collections.emptyList();

    if (statements.length == 1) {
      if (statements[0] instanceof PsiReturnStatement) {
        List<PreContract> result = handleDelegation(((PsiReturnStatement)statements[0]).getReturnValue(), false);
        if (result != null) {
          return result;
        }
      }
      else if (statements[0] instanceof PsiExpressionStatement && ((PsiExpressionStatement)statements[0]).getExpression() instanceof PsiMethodCallExpression) {
        List<PreContract> result = handleDelegation(((PsiExpressionStatement)statements[0]).getExpression(), false);
        if (result != null) return result;
      }
    }

    return visitStatements(Collections.singletonList(myEmptyConstraints), statements);
  }

  @Nullable
  private static List<PreContract> handleDelegation(final PsiExpression expression, final boolean negated) {
    if (expression instanceof PsiParenthesizedExpression) {
      return handleDelegation(((PsiParenthesizedExpression)expression).getExpression(), negated);
    }

    if (expression instanceof PsiPrefixExpression && ((PsiPrefixExpression)expression).getOperationTokenType() == JavaTokenType.EXCL) {
      return handleDelegation(((PsiPrefixExpression)expression).getOperand(), !negated);
    }

    if (expression instanceof PsiMethodCallExpression) {
      return Collections.singletonList(new DelegationContract((PsiMethodCallExpression)expression, negated));
    }

    return null;
  }

  @NotNull
  private List<PreContract> visitExpression(final List<ValueConstraint[]> states, @Nullable PsiExpression expr) {
    if (states.isEmpty()) return Collections.emptyList();
    if (states.size() > 300) return Collections.emptyList(); // too complex

    if (expr instanceof PsiPolyadicExpression) {
      PsiExpression[] operands = ((PsiPolyadicExpression)expr).getOperands();
      IElementType op = ((PsiPolyadicExpression)expr).getOperationTokenType();
      if (operands.length == 2 && (op == JavaTokenType.EQEQ || op == JavaTokenType.NE)) {
        return asPreContracts(visitEqualityComparison(states, operands[0], operands[1], op == JavaTokenType.EQEQ));
      }
      if (op == JavaTokenType.ANDAND || op == JavaTokenType.OROR) {
        return asPreContracts(visitLogicalOperation(operands, op == JavaTokenType.ANDAND, states));
      }
    }

    if (expr instanceof PsiConditionalExpression) {
      List<PreContract> conditionResults = visitExpression(states, ((PsiConditionalExpression)expr).getCondition());
      return ContainerUtil.concat(
        visitExpression(antecedentsReturning(conditionResults, TRUE_VALUE), ((PsiConditionalExpression)expr).getThenExpression()),
        visitExpression(antecedentsReturning(conditionResults, FALSE_VALUE), ((PsiConditionalExpression)expr).getElseExpression()));
    }


    if (expr instanceof PsiParenthesizedExpression) {
      return visitExpression(states, ((PsiParenthesizedExpression)expr).getExpression());
    }
    if (expr instanceof PsiTypeCastExpression) {
      return visitExpression(states, ((PsiTypeCastExpression)expr).getOperand());
    }

    if (expr instanceof PsiPrefixExpression && ((PsiPrefixExpression)expr).getOperationTokenType() == JavaTokenType.EXCL) {
      List<PreContract> result = ContainerUtil.newArrayList();
      for (PreContract contract : visitExpression(states, ((PsiPrefixExpression)expr).getOperand())) {
        ContainerUtil.addIfNotNull(result, NegatingContract.negate(contract));
      }
      return result;
    }

    if (expr instanceof PsiInstanceOfExpression) {
      final int parameter = resolveParameter(((PsiInstanceOfExpression)expr).getOperand());
      if (parameter >= 0) {
        return asPreContracts(ContainerUtil.mapNotNull(states, state -> contractWithConstraint(state, parameter, NULL_VALUE, FALSE_VALUE)));
      }
    }

    if (expr instanceof PsiNewExpression) {
      return asPreContracts(toContracts(states, NOT_NULL_VALUE));
    }
    if (expr instanceof PsiMethodCallExpression) {
      return Collections.singletonList(new MethodCallContract((PsiMethodCallExpression)expr, states));
    }

    final ValueConstraint constraint = getLiteralConstraint(expr);
    if (constraint != null) {
      return asPreContracts(toContracts(states, constraint));
    }

    int paramIndex = resolveParameter(expr);
    if (paramIndex >= 0) {
      List<MethodContract> result = ContainerUtil.newArrayList();
      for (ValueConstraint[] state : states) {
        if (state[paramIndex] != ANY_VALUE) {
          // the second 'o' reference in cases like: if (o != null) return o;
          result.add(new MethodContract(state, state[paramIndex]));
        } else if (textMatches(getParameter(paramIndex).getTypeElement(), PsiKeyword.BOOLEAN)) {
          // if (boolValue) ...
          ContainerUtil.addIfNotNull(result, contractWithConstraint(state, paramIndex, TRUE_VALUE, TRUE_VALUE));
          ContainerUtil.addIfNotNull(result, contractWithConstraint(state, paramIndex, FALSE_VALUE, FALSE_VALUE));
        }
      }
      return asPreContracts(result);
    }

    return Collections.emptyList();
  }

  @NotNull
  private static List<PreContract> asPreContracts(List<MethodContract> contracts) {
    return ContainerUtil.map(contracts, KnownContract::new);
  }

  @Nullable
  private MethodContract contractWithConstraint(ValueConstraint[] state,
                                                       int parameter, ValueConstraint paramConstraint,
                                                       ValueConstraint returnValue) {
    ValueConstraint[] newState = withConstraint(state, parameter, paramConstraint, myMethod);
    return newState == null ? null : new MethodContract(newState, returnValue);
  }

  private static boolean textMatches(@Nullable PsiTypeElement typeElement, @NotNull String text) {
    return typeElement != null && typeElement.textMatches(text);
  }

  private List<MethodContract> visitEqualityComparison(List<ValueConstraint[]> states,
                                                       PsiExpression op1,
                                                       PsiExpression op2,
                                                       boolean equality) {
    int parameter = resolveParameter(op1);
    ValueConstraint constraint = getLiteralConstraint(op2);
    if (parameter < 0 || constraint == null) {
      parameter = resolveParameter(op2);
      constraint = getLiteralConstraint(op1);
    }
    if (parameter >= 0 && constraint != null) {
      List<MethodContract> result = ContainerUtil.newArrayList();
      for (ValueConstraint[] state : states) {
        if (constraint == NOT_NULL_VALUE) {
          if (!(getParameter(parameter).getType() instanceof PsiPrimitiveType)) {
            ContainerUtil.addIfNotNull(result, contractWithConstraint(state, parameter, NULL_VALUE, equality ? FALSE_VALUE : TRUE_VALUE));
          }
        } else {
          ContainerUtil.addIfNotNull(result, contractWithConstraint(state, parameter, constraint, equality ? TRUE_VALUE : FALSE_VALUE));
          ContainerUtil.addIfNotNull(result, contractWithConstraint(state, parameter, negateConstraint(constraint),
                                                                    equality ? FALSE_VALUE : TRUE_VALUE));
        }
      }
      return result;
    }
    return Collections.emptyList();
  }

  private PsiParameter getParameter(int parameter) {
    return getParameter(parameter, myMethod);
  }

  private static PsiParameter getParameter(int parameter, PsiMethod method) {
    return method.getParameterList().getParameters()[parameter];
  }

  static List<MethodContract> toContracts(List<ValueConstraint[]> states, ValueConstraint constraint) {
    return ContainerUtil.map(states, state -> new MethodContract(state, constraint));
  }

  private List<MethodContract> visitLogicalOperation(PsiExpression[] operands, boolean conjunction, List<ValueConstraint[]> states) {
    ValueConstraint breakValue = conjunction ? FALSE_VALUE : TRUE_VALUE;
    List<MethodContract> finalStates = ContainerUtil.newArrayList();
    for (PsiExpression operand : operands) {
      List<PreContract> opResults = visitExpression(states, operand);
      finalStates.addAll(ContainerUtil.filter(knownContracts(opResults), contract -> contract.returnValue == breakValue));
      states = antecedentsReturning(opResults, negateConstraint(breakValue));
    }
    finalStates.addAll(toContracts(states, negateConstraint(breakValue)));
    return finalStates;
  }

  private static List<MethodContract> knownContracts(List<PreContract> values) {
    return ContainerUtil.mapNotNull(values, pc -> pc instanceof KnownContract ? ((KnownContract)pc).getContract() : null);
  }

  private static List<ValueConstraint[]> antecedentsReturning(List<PreContract> values, ValueConstraint result) {
    return ContainerUtil.mapNotNull(knownContracts(values), contract -> contract.returnValue == result ? contract.arguments : null);
  }

  private static class CodeBlockContracts {
    List<PreContract> accumulated = new ArrayList<>();
    List<PsiDeclarationStatement> declarations = new ArrayList<>();

    void addAll(List<PreContract> contracts) {
      if (contracts.isEmpty()) return;

      if (declarations.isEmpty()) {
        accumulated.addAll(contracts);
      } else {
        accumulated.add(new SideEffectFilter(getVariableInitializers(), contracts));
      }
    }

    @NotNull
    List<PsiExpression> getVariableInitializers() {
      return JBIterable.from(declarations).
        flatMap(s -> JBIterable.of(s.getDeclaredElements())).
        filter(PsiVariable.class).
        flatMap(var -> JBIterable.of(var.getInitializer())).
        toList();
    }
  }

  @NotNull
  private List<PreContract> visitStatements(List<ValueConstraint[]> states, PsiStatement... statements) {
    CodeBlockContracts result = new CodeBlockContracts();
    for (PsiStatement statement : statements) {
      if (statement instanceof PsiBlockStatement) {
        result.addAll(visitStatements(states, ((PsiBlockStatement)statement).getCodeBlock().getStatements()));
      }
      else if (statement instanceof PsiIfStatement) {
        List<PreContract> conditionResults = visitExpression(states, ((PsiIfStatement)statement).getCondition());

        PsiStatement thenBranch = ((PsiIfStatement)statement).getThenBranch();
        if (thenBranch != null) {
          result.addAll(visitStatements(antecedentsReturning(conditionResults, TRUE_VALUE), thenBranch));
        }

        List<ValueConstraint[]> falseStates = antecedentsReturning(conditionResults, FALSE_VALUE);
        PsiStatement elseBranch = ((PsiIfStatement)statement).getElseBranch();
        if (elseBranch != null) {
          result.addAll(visitStatements(falseStates, elseBranch));
        } else {
          states = falseStates;
          continue;
        }
      }
      else if (statement instanceof PsiWhileStatement) {
        states = antecedentsReturning(visitExpression(states, ((PsiWhileStatement)statement).getCondition()), FALSE_VALUE);
        continue;
      }
      else if (statement instanceof PsiThrowStatement) {
        result.addAll(asPreContracts(toContracts(states, THROW_EXCEPTION)));
      }
      else if (statement instanceof PsiReturnStatement) {
        result.addAll(visitExpression(states, ((PsiReturnStatement)statement).getReturnValue()));
      }
      else if (statement instanceof PsiAssertStatement) {
        List<PreContract> conditionResults = visitExpression(states, ((PsiAssertStatement)statement).getAssertCondition());
        result.addAll(asPreContracts(toContracts(antecedentsReturning(conditionResults, FALSE_VALUE), THROW_EXCEPTION)));
      }
      else if (statement instanceof PsiDeclarationStatement) {
        result.declarations.add((PsiDeclarationStatement)statement);
        continue;
      }
      else if (statement instanceof PsiDoWhileStatement) {
        result.addAll(visitStatements(states, ((PsiDoWhileStatement)statement).getBody()));
      }

      break; // visit only the first statement unless it's 'if' whose 'then' always returns and the next statement is effectively 'else'
    }
    return result.accumulated;
  }

  @Nullable
  static ValueConstraint getLiteralConstraint(@Nullable PsiExpression expr) {
    if (expr instanceof PsiLiteralExpression) {
      if (expr.textMatches(PsiKeyword.TRUE)) return TRUE_VALUE;
      if (expr.textMatches(PsiKeyword.FALSE)) return FALSE_VALUE;
      if (expr.textMatches(PsiKeyword.NULL)) return NULL_VALUE;
      return NOT_NULL_VALUE;
    }
    return null;
  }

  static ValueConstraint negateConstraint(@NotNull ValueConstraint constraint) {
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (constraint) {
      case NULL_VALUE: return NOT_NULL_VALUE;
      case NOT_NULL_VALUE: return NULL_VALUE;
      case TRUE_VALUE: return FALSE_VALUE;
      case FALSE_VALUE: return TRUE_VALUE;
    }
    return constraint;
  }

  private int resolveParameter(@Nullable PsiExpression expr) {
    return resolveParameter(expr, myMethod);
  }

  static int resolveParameter(@Nullable PsiExpression expr, PsiMethod method) {
    if (expr instanceof PsiReferenceExpression && !((PsiReferenceExpression)expr).isQualified()) {
      String name = ((PsiReferenceExpression)expr).getReferenceName();
      if (name == null) return -1;

      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        if (name.equals(parameters[i].getName())) {
          return i;
        }
      }
    }
    return -1;
  }

  @Nullable
  static ValueConstraint[] withConstraint(ValueConstraint[] constraints, int index, ValueConstraint constraint, PsiMethod method) {
    if (constraints[index] == constraint) return constraints;

    ValueConstraint negated = negateConstraint(constraint);
    if (negated != constraint && constraints[index] == negated) {
      return null;
    }

    if (constraint == NULL_VALUE && NullableNotNullManager.isNotNull(getParameter(index, method))) {
      return null;
    }

    ValueConstraint[] copy = constraints.clone();
    copy[index] = constraint;
    return copy;
  }

}