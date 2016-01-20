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
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    
    return CachedValuesManager.getCachedValue(method, new CachedValueProvider<List<MethodContract>>() {
      @Nullable
      @Override
      public Result<List<MethodContract>> compute() {
        List<MethodContract> result = RecursionManager.doPreventingRecursion(method, true, new Computable<List<MethodContract>>() {
          @Override
          public List<MethodContract> compute() {
            return new ContractInferenceInterpreter(method).inferContracts();
          }
        });
        if (result == null) result = Collections.emptyList();
        return Result.create(result, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
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
    List<MethodContract> contracts = doInferContracts();
    if (contracts.isEmpty()) return contracts;
    
    PsiTypeElement typeElement = myMethod.getReturnTypeElement();
    final PsiType returnType = typeElement == null ? null : typeElement.getType();
    boolean referenceTypeReturned = !(returnType instanceof PsiPrimitiveType);
    final boolean notNull = referenceTypeReturned && 
                            NullableNotNullManager.getInstance(myMethod.getProject()).isNotNull(myMethod, false);
    if (referenceTypeReturned) {
      contracts = boxReturnValues(contracts);
    }
    List<MethodContract> compatible = ContainerUtil.filter(contracts, new Condition<MethodContract>() {
      @Override
      public boolean value(MethodContract contract) {
        if (notNull && contract.returnValue == NOT_NULL_VALUE) {
          return false;
        }
        return InferenceFromSourceUtil.isReturnTypeCompatible(returnType, contract.returnValue);
      }
    });
    if (compatible.size() > ContractInference.MAX_CONTRACT_COUNT) {
      LOG.debug("Too many contracts for " + PsiUtil.getMemberQualifiedName(myMethod) + ", shrinking the list");
      return compatible.subList(0, ContractInference.MAX_CONTRACT_COUNT);
    }
    return compatible;
  }

  @NotNull
  private static List<MethodContract> boxReturnValues(List<MethodContract> contracts) {
    return ContainerUtil.mapNotNull(contracts, new Function<MethodContract, MethodContract>() {
      @Override
      public MethodContract fun(MethodContract contract) {
        if (contract.returnValue == FALSE_VALUE || contract.returnValue == TRUE_VALUE) {
          return new MethodContract(contract.arguments, NOT_NULL_VALUE);
        }
        return contract;
      }
    });
  }

  private List<MethodContract> doInferContracts() {
    PsiCodeBlock body = myMethod.getBody();
    PsiStatement[] statements = body == null ? PsiStatement.EMPTY_ARRAY : body.getStatements();
    if (statements.length == 0) return Collections.emptyList();

    if (statements.length == 1) {
      if (statements[0] instanceof PsiReturnStatement) {
        List<MethodContract> result = handleDelegation(((PsiReturnStatement)statements[0]).getReturnValue(), false);
        if (result != null) {
          return result;
        }
      }
      else if (statements[0] instanceof PsiExpressionStatement && ((PsiExpressionStatement)statements[0]).getExpression() instanceof PsiMethodCallExpression) {
        List<MethodContract> result = handleDelegation(((PsiExpressionStatement)statements[0]).getExpression(), false);
        if (result != null) return result;
      }
    }

    return visitStatements(Collections.singletonList(myEmptyConstraints), statements);
  }

  @Nullable
  private List<MethodContract> handleDelegation(final PsiExpression expression, final boolean negated) {
    if (expression instanceof PsiParenthesizedExpression) {
      return handleDelegation(((PsiParenthesizedExpression)expression).getExpression(), negated);
    }

    if (expression instanceof PsiPrefixExpression && ((PsiPrefixExpression)expression).getOperationTokenType() == JavaTokenType.EXCL) {
      return handleDelegation(((PsiPrefixExpression)expression).getOperand(), !negated);
    }

    if (expression instanceof PsiMethodCallExpression) {
      return handleCallDelegation((PsiMethodCallExpression)expression, negated);
    }

    return null;
  }

  private List<MethodContract> handleCallDelegation(PsiMethodCallExpression expression, final boolean negated) {
    JavaResolveResult result = expression.resolveMethodGenerics();
    final PsiMethod targetMethod = (PsiMethod)result.getElement();
    if (targetMethod == null) return Collections.emptyList();

    final PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
    final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
    final boolean varArgCall = MethodCallInstruction.isVarArgCall(targetMethod, result.getSubstitutor(), arguments, parameters);

    final boolean notNull = NullableNotNullManager.isNotNull(targetMethod);
    List<MethodContract> fromDelegate = ContainerUtil.mapNotNull(ControlFlowAnalyzer.getMethodContracts(targetMethod), new NullableFunction<MethodContract, MethodContract>() {
      @Nullable
      @Override
      public MethodContract fun(MethodContract delegateContract) {
        ValueConstraint[] answer = myEmptyConstraints;
        for (int i = 0; i < delegateContract.arguments.length; i++) {
          if (i >= arguments.length) return null;
          ValueConstraint argConstraint = delegateContract.arguments[i];
          if (argConstraint != ANY_VALUE) {
            if (varArgCall && i >= parameters.length - 1) {
              if (argConstraint == NULL_VALUE) {
                return null;
              }
              break;
            }

            int paramIndex = resolveParameter(arguments[i]);
            if (paramIndex < 0) {
              if (argConstraint != getLiteralConstraint(arguments[i])) {
                return null;
              }
            }
            else {
              answer = withConstraint(answer, paramIndex, argConstraint);
              if (answer == null) {
                return null;
              }
            }
          }
        }
        ValueConstraint returnValue = negated ? negateConstraint(delegateContract.returnValue) : delegateContract.returnValue;
        if (notNull && returnValue != THROW_EXCEPTION) {
          returnValue = NOT_NULL_VALUE;
        }
        return answer == null ? null : new MethodContract(answer, returnValue);
      }
    });
    if (notNull) {
      return ContainerUtil.concat(fromDelegate, Collections.singletonList(new MethodContract(myEmptyConstraints, NOT_NULL_VALUE)));
    }
    return fromDelegate;
  }

  @NotNull
  private List<MethodContract> visitExpression(final List<ValueConstraint[]> states, @Nullable PsiExpression expr) {
    if (states.isEmpty()) return Collections.emptyList();
    if (states.size() > 300) return Collections.emptyList(); // too complex

    if (expr instanceof PsiPolyadicExpression) {
      PsiExpression[] operands = ((PsiPolyadicExpression)expr).getOperands();
      IElementType op = ((PsiPolyadicExpression)expr).getOperationTokenType();
      if (operands.length == 2 && (op == JavaTokenType.EQEQ || op == JavaTokenType.NE)) {
        return visitEqualityComparison(states, operands[0], operands[1], op == JavaTokenType.EQEQ);
      }
      if (op == JavaTokenType.ANDAND || op == JavaTokenType.OROR) {
        return visitLogicalOperation(operands, op == JavaTokenType.ANDAND, states);
      }
    }

    if (expr instanceof PsiConditionalExpression) {
      List<MethodContract> conditionResults = visitExpression(states, ((PsiConditionalExpression)expr).getCondition());
      return ContainerUtil.concat(
        visitExpression(antecedentsOf(filterReturning(conditionResults, TRUE_VALUE)), ((PsiConditionalExpression)expr).getThenExpression()),
        visitExpression(antecedentsOf(filterReturning(conditionResults, FALSE_VALUE)), ((PsiConditionalExpression)expr).getElseExpression()));
    }


    if (expr instanceof PsiParenthesizedExpression) {
      return visitExpression(states, ((PsiParenthesizedExpression)expr).getExpression());
    }
    if (expr instanceof PsiTypeCastExpression) {
      return visitExpression(states, ((PsiTypeCastExpression)expr).getOperand());
    }

    if (expr instanceof PsiPrefixExpression && ((PsiPrefixExpression)expr).getOperationTokenType() == JavaTokenType.EXCL) {
      List<MethodContract> result = ContainerUtil.newArrayList();
      for (MethodContract contract : visitExpression(states, ((PsiPrefixExpression)expr).getOperand())) {
        if (contract.returnValue == TRUE_VALUE || contract.returnValue == FALSE_VALUE) {
          result.add(new MethodContract(contract.arguments, negateConstraint(contract.returnValue)));
        }
      }
      return result;
    }

    if (expr instanceof PsiInstanceOfExpression) {
      final int parameter = resolveParameter(((PsiInstanceOfExpression)expr).getOperand());
      if (parameter >= 0) {
        return ContainerUtil.mapNotNull(states, new Function<ValueConstraint[], MethodContract>() {
          @Override
          public MethodContract fun(ValueConstraint[] state) {
            ValueConstraint paramConstraint = NULL_VALUE;
            ValueConstraint returnValue = FALSE_VALUE;
            return contractWithConstraint(state, parameter, paramConstraint, returnValue);
          }
        });
      }
    }

    if (expr instanceof PsiNewExpression) {
      return toContracts(states, NOT_NULL_VALUE);
    }
    if (expr instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)expr).resolveMethod();
      if (method != null && NullableNotNullManager.isNotNull(method)) {
        return toContracts(states, NOT_NULL_VALUE);
      }
    }

    final ValueConstraint constraint = getLiteralConstraint(expr);
    if (constraint != null) {
      return toContracts(states, constraint);
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
      return result;
    }

    return Collections.emptyList();
  }

  @Nullable
  private MethodContract contractWithConstraint(ValueConstraint[] state,
                                                       int parameter, ValueConstraint paramConstraint,
                                                       ValueConstraint returnValue) {
    ValueConstraint[] newState = withConstraint(state, parameter, paramConstraint);
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
    return myMethod.getParameterList().getParameters()[parameter];
  }

  private static List<MethodContract> toContracts(List<ValueConstraint[]> states,
                                                  final ValueConstraint constraint) {
    return ContainerUtil.map(states, new Function<ValueConstraint[], MethodContract>() {
      @Override
      public MethodContract fun(ValueConstraint[] state) {
        return new MethodContract(state, constraint);
      }
    });
  }

  private List<MethodContract> visitLogicalOperation(PsiExpression[] operands, boolean conjunction, List<ValueConstraint[]> states) {
    ValueConstraint breakValue = conjunction ? FALSE_VALUE : TRUE_VALUE;
    List<MethodContract> finalStates = ContainerUtil.newArrayList();
    for (PsiExpression operand : operands) {
      List<MethodContract> opResults = visitExpression(states, operand);
      finalStates.addAll(filterReturning(opResults, breakValue));
      states = antecedentsOf(filterReturning(opResults, negateConstraint(breakValue)));
    }
    finalStates.addAll(toContracts(states, negateConstraint(breakValue)));
    return finalStates;
  }

  private static List<ValueConstraint[]> antecedentsOf(List<MethodContract> values) {
    return ContainerUtil.map(values, new Function<MethodContract, ValueConstraint[]>() {
      @Override
      public ValueConstraint[] fun(MethodContract contract) {
        return contract.arguments;
      }
    });
  }

  private static List<MethodContract> filterReturning(List<MethodContract> values, final ValueConstraint result) {
    return ContainerUtil.filter(values, new Condition<MethodContract>() {
      @Override
      public boolean value(MethodContract contract) {
        return contract.returnValue == result;
      }
    });
  }

  @NotNull
  private List<MethodContract> visitStatements(List<ValueConstraint[]> states, PsiStatement... statements) {
    List<MethodContract> result = ContainerUtil.newArrayList();
    for (PsiStatement statement : statements) {
      if (statement instanceof PsiBlockStatement) {
        result.addAll(visitStatements(states, ((PsiBlockStatement)statement).getCodeBlock().getStatements()));
      }
      else if (statement instanceof PsiIfStatement) {
        List<MethodContract> conditionResults = visitExpression(states, ((PsiIfStatement)statement).getCondition());

        PsiStatement thenBranch = ((PsiIfStatement)statement).getThenBranch();
        if (thenBranch != null) {
          result.addAll(visitStatements(antecedentsOf(filterReturning(conditionResults, TRUE_VALUE)), thenBranch));
        }

        List<ValueConstraint[]> falseStates = antecedentsOf(filterReturning(conditionResults, FALSE_VALUE));
        PsiStatement elseBranch = ((PsiIfStatement)statement).getElseBranch();
        if (elseBranch != null) {
          result.addAll(visitStatements(falseStates, elseBranch));
        } else {
          states = falseStates;
          continue;
        }
      }
      else if (statement instanceof PsiThrowStatement) {
        result.addAll(toContracts(states, THROW_EXCEPTION));
      }
      else if (statement instanceof PsiReturnStatement) {
        result.addAll(visitExpression(states, ((PsiReturnStatement)statement).getReturnValue()));
      }
      else if (statement instanceof PsiAssertStatement) {
        List<MethodContract> conditionResults = visitExpression(states, ((PsiAssertStatement)statement).getAssertCondition());
        result.addAll(toContracts(antecedentsOf(filterReturning(conditionResults, FALSE_VALUE)), THROW_EXCEPTION));
      }
      else if (statement instanceof PsiDeclarationStatement && !mayHaveSideEffects((PsiDeclarationStatement)statement)) {
        continue;
      }
      else if (statement instanceof PsiDoWhileStatement) {
        result.addAll(visitStatements(states, ((PsiDoWhileStatement)statement).getBody()));
      }

      break; // visit only the first statement unless it's 'if' whose 'then' always returns and the next statement is effectively 'else'
    }
    return result;
  }

  private static boolean mayHaveSideEffects(PsiDeclarationStatement statement) {
    for (PsiElement element : statement.getDeclaredElements()) {
      if (element instanceof PsiVariable) {
        PsiExpression initializer = ((PsiVariable)element).getInitializer();
        if (initializer != null && SideEffectChecker.mayHaveSideEffects(initializer)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private static ValueConstraint getLiteralConstraint(@Nullable PsiExpression expr) {
    if (expr instanceof PsiLiteralExpression) {
      if (expr.textMatches(PsiKeyword.TRUE)) return TRUE_VALUE;
      if (expr.textMatches(PsiKeyword.FALSE)) return FALSE_VALUE;
      if (expr.textMatches(PsiKeyword.NULL)) return NULL_VALUE;
      return NOT_NULL_VALUE;
    }
    return null;
  }

  private static ValueConstraint negateConstraint(@NotNull ValueConstraint constraint) {
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
    if (expr instanceof PsiReferenceExpression && !((PsiReferenceExpression)expr).isQualified()) {
      String name = expr.getText();
      PsiParameter[] parameters = myMethod.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        if (name.equals(parameters[i].getName())) {
          return i;
        }
      }
    }
    return -1;
  }

  @Nullable
  private ValueConstraint[] withConstraint(ValueConstraint[] constraints, int index, ValueConstraint constraint) {
    if (constraints[index] == constraint) return constraints;

    ValueConstraint negated = negateConstraint(constraint);
    if (negated != constraint && constraints[index] == negated) {
      return null;
    }

    if (constraint == NULL_VALUE && NullableNotNullManager.isNotNull(getParameter(index))) {
      return null;
    }

    ValueConstraint[] copy = constraints.clone();
    copy[index] = constraint;
    return copy;
  }

}