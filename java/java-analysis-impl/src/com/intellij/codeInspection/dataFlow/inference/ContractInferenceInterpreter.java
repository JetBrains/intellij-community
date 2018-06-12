// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference;

import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.ContractReturnValue.*;
import static com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint.*;
import static com.intellij.psi.impl.source.JavaLightTreeUtil.*;
import static com.intellij.psi.impl.source.tree.JavaElementType.*;
import static com.intellij.psi.impl.source.tree.LightTreeUtil.firstChildOfType;
import static com.intellij.psi.impl.source.tree.LightTreeUtil.getChildrenOfType;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

class ContractInferenceInterpreter {
  private static final TokenSet UNARY_INCREMENT_DECREMENT = TokenSet.create(JavaTokenType.PLUSPLUS, JavaTokenType.MINUSMINUS);
  private final LighterAST myTree;
  private final LighterASTNode myMethod;
  private final LighterASTNode myBody;

  public ContractInferenceInterpreter(LighterAST tree, LighterASTNode method, LighterASTNode body) {
    myTree = tree;
    myMethod = method;
    myBody = body;
  }

  @NotNull
  private List<LighterASTNode> getParameters() {
    LighterASTNode paramList = firstChildOfType(myTree, myMethod, PARAMETER_LIST);
    return paramList != null ? getChildrenOfType(myTree, paramList, PARAMETER) : emptyList();
  }

  @NotNull
  List<PreContract> inferContracts(List<LighterASTNode> statements) {
    if (statements.isEmpty()) return emptyList();

    if (statements.size() == 1) {
      List<PreContract> result = handleSingleStatement(statements.get(0));
      if (result != null) return result;
    }

    List<PreContract> contracts =
      visitStatements(singletonList(StandardMethodContract.createConstraintArray(getParameters().size())), statements);
    if (contracts.isEmpty()) {
      ContractReturnValue value = getDefaultReturnValue(statements);
      if (!value.isFail() && !value.equals(returnAny())) {
        contracts = singletonList(
          new KnownContract(new StandardMethodContract(StandardMethodContract.createConstraintArray(getParameters().size()), value)));
      }
    }
    return contracts;
  }

  @Nullable
  private List<PreContract> handleSingleStatement(LighterASTNode statement) {
    if (statement.getTokenType() == RETURN_STATEMENT) {
      LighterASTNode returned = findExpressionChild(myTree, statement);
      return getLiteralConstraint(returned) != null ? emptyList() : handleDelegation(returned, false);
    }
    if (statement.getTokenType() == EXPRESSION_STATEMENT) {
      LighterASTNode expr = findExpressionChild(myTree, statement);
      return expr != null && expr.getTokenType() == METHOD_CALL_EXPRESSION ? handleDelegation(expr, false) : null;
    }
    return null;
  }

  @Nullable
  private LighterASTNode getCodeBlock(@Nullable LighterASTNode parent) {
    return firstChildOfType(myTree, parent, CODE_BLOCK);
  }

  @NotNull
  static List<LighterASTNode> getStatements(@Nullable LighterASTNode codeBlock, LighterAST tree) {
    return codeBlock == null ? emptyList() : getChildrenOfType(tree, codeBlock, ElementType.JAVA_STATEMENT_BIT_SET);
  }

  @Nullable
  private List<PreContract> handleDelegation(@Nullable LighterASTNode expression, boolean negated) {
    if (expression == null) return null;
    if (expression.getTokenType() == PARENTH_EXPRESSION) {
      return handleDelegation(findExpressionChild(myTree, expression), negated);
    }

    if (isNegationExpression(expression)) {
      return handleDelegation(findExpressionChild(myTree, expression), !negated);
    }

    if (expression.getTokenType() == METHOD_CALL_EXPRESSION) {
      return singletonList(new DelegationContract(ExpressionRange.create(expression, myBody.getStartOffset()), negated));
    }

    return null;
  }

  private boolean isNegationExpression(@Nullable LighterASTNode expression) {
    return expression != null && expression.getTokenType() == PREFIX_EXPRESSION && firstChildOfType(myTree, expression, JavaTokenType.EXCL) != null;
  }

  private ContractReturnValue getDefaultReturnValue(List<LighterASTNode> statements) {
    class ReturnValueVisitor extends RecursiveLighterASTNodeWalkingVisitor {
      public ContractReturnValue returnValue = fail();

      private BitSet assignedParameters;

      ReturnValueVisitor() {
        super(myTree);
      }

      @Override
      public void visitNode(@NotNull LighterASTNode element) {
        IElementType type = element.getTokenType();
        if (type == CLASS || type == LAMBDA_EXPRESSION) return;
        if (returnValue.equals(returnAny())) {
          return;
        }
        if (type == ASSIGNMENT_EXPRESSION || type == POSTFIX_EXPRESSION ||
            (type == PREFIX_EXPRESSION && firstChildOfType(myTree, element, UNARY_INCREMENT_DECREMENT) != null)) {
          LighterASTNode expression = skipParenthesesCastsDown(myTree, findExpressionChild(myTree, element));
          int paramIndex = resolveParameter(expression);
          if (paramIndex >= 0) {
            if (assignedParameters == null) {
              assignedParameters = new BitSet();
            }
            assignedParameters.set(paramIndex);
            if (returnValue.equals(returnParameter(paramIndex))) {
              returnValue = returnAny();
            }
          }
        }
        if (type == RETURN_STATEMENT) {
          LighterASTNode expression = findExpressionChild(myTree, element);
          ContractReturnValue newReturnValue = expressionToReturnValue(expression);
          if (returnValue.isFail()) {
            returnValue = newReturnValue;
          }
          else if (!returnValue.equals(newReturnValue)) {
            returnValue = returnAny();
          }
        }
        super.visitNode(element);
      }

      @NotNull
      private ContractReturnValue expressionToReturnValue(LighterASTNode expression) {
        expression = skipParenthesesDown(myTree, expression);
        if (expression == null) return returnAny();
        IElementType type = expression.getTokenType();
        if (type == NEW_EXPRESSION) {
          return returnNew();
        }
        if (type == THIS_EXPRESSION) {
          return returnThis();
        }
        if (type == REFERENCE_EXPRESSION) {
          int paramIndex = resolveParameter(expression);
          if (paramIndex >= 0 && (assignedParameters == null || !assignedParameters.get(paramIndex))) {
            return returnParameter(paramIndex);
          }
        }
        return returnAny();
      }
    }
    ReturnValueVisitor visitor = new ReturnValueVisitor();
    for (LighterASTNode statement : statements) {
      visitor.visitNode(statement);
    }
    return visitor.returnValue;
  }

  @NotNull
  private List<PreContract> visitExpression(final List<ValueConstraint[]> states, @Nullable LighterASTNode expr) {
    if (expr == null) return emptyList();
    if (states.isEmpty()) return emptyList();
    if (states.size() > 300) return emptyList(); // too complex

    IElementType type = expr.getTokenType();
    if (type == POLYADIC_EXPRESSION || type == BINARY_EXPRESSION) {
      return visitPolyadic(states, expr);
    }

    if (type == CONDITIONAL_EXPRESSION) {
      List<LighterASTNode> children = getExpressionChildren(myTree, expr);
      if (children.size() != 3) return emptyList();

      List<PreContract> conditionResults = visitExpression(states, children.get(0));
      return ContainerUtil.concat(
        visitExpression(antecedentsReturning(conditionResults, returnTrue()), children.get(1)),
        visitExpression(antecedentsReturning(conditionResults, returnFalse()), children.get(2)));
    }


    if (type == PARENTH_EXPRESSION) {
      return visitExpression(states, findExpressionChild(myTree, expr));
    }
    if (type == TYPE_CAST_EXPRESSION) {
      return visitExpression(states, findExpressionChild(myTree, expr));
    }

    if (isNegationExpression(expr)) {
      return ContainerUtil.mapNotNull(visitExpression(states, findExpressionChild(myTree, expr)), PreContract::negate);
    }

    if (type == INSTANCE_OF_EXPRESSION) {
      final int parameter = resolveParameter(findExpressionChild(myTree, expr));
      if (parameter >= 0) {
        return asPreContracts(ContainerUtil.mapNotNull(states, state -> contractWithConstraint(state, parameter, NULL_VALUE, returnFalse())));
      }
    }

    if (type == NEW_EXPRESSION) {
      return asPreContracts(toContracts(states, returnNew()));
    }
    if (type == THIS_EXPRESSION) {
      return asPreContracts(toContracts(states, returnThis()));
    }
    if (type == METHOD_CALL_EXPRESSION) {
      return singletonList(new MethodCallContract(ExpressionRange.create(expr, myBody.getStartOffset()),
                                                              ContainerUtil.map(states, Arrays::asList)));
    }

    final ValueConstraint constraint = getLiteralConstraint(expr);
    if (constraint != null) {
      return asPreContracts(toContracts(states, constraint.asReturnValue()));
    }

    int paramIndex = resolveParameter(expr);
    if (paramIndex >= 0) {
      List<StandardMethodContract> result = ContainerUtil.newArrayList();
      for (ValueConstraint[] state : states) {
        if (state[paramIndex] == TRUE_VALUE || state[paramIndex] == FALSE_VALUE || state[paramIndex] == NULL_VALUE) {
          // like "if(x == null) return x": no need to refer to parameter
          result.add(new StandardMethodContract(state, state[paramIndex].asReturnValue()));
        } else if (JavaTokenType.BOOLEAN_KEYWORD == getPrimitiveParameterType(paramIndex)) {
          // if (boolValue) ...
          ContainerUtil.addIfNotNull(result, contractWithConstraint(state, paramIndex, TRUE_VALUE, returnTrue()));
          ContainerUtil.addIfNotNull(result, contractWithConstraint(state, paramIndex, FALSE_VALUE, returnFalse()));
        } else {
          result.add(new StandardMethodContract(state, returnParameter(paramIndex)));
        }
      }
      return asPreContracts(result);
    }

    return emptyList();
  }

  @NotNull
  private List<PreContract> visitPolyadic(List<ValueConstraint[]> states, @NotNull LighterASTNode expr) {
    if (firstChildOfType(myTree, expr, JavaTokenType.PLUS) != null) {
      return asPreContracts(ContainerUtil.map(states, s -> new StandardMethodContract(s, returnNotNull())));
    }

    List<LighterASTNode> operands = getExpressionChildren(myTree, expr);
    if (operands.size() == 2) {
      boolean equality = firstChildOfType(myTree, expr, JavaTokenType.EQEQ) != null;
      if (equality || firstChildOfType(myTree, expr, JavaTokenType.NE) != null) {
        return asPreContracts(visitEqualityComparison(states, operands.get(0), operands.get(1), equality));
      }
    }
    boolean logicalAnd = firstChildOfType(myTree, expr, JavaTokenType.ANDAND) != null;
    if (logicalAnd || firstChildOfType(myTree, expr, JavaTokenType.OROR) != null) {
      return asPreContracts(visitLogicalOperation(operands, logicalAnd, states));
    }
    return emptyList();
  }

  @NotNull
  private static List<PreContract> asPreContracts(List<StandardMethodContract> contracts) {
    return ContainerUtil.map(contracts, KnownContract::new);
  }

  @Nullable
  private static StandardMethodContract contractWithConstraint(ValueConstraint[] state,
                                                               int parameter, ValueConstraint paramConstraint,
                                                               ContractReturnValue returnValue) {
    ValueConstraint[] newState = withConstraint(state, parameter, paramConstraint);
    return newState == null ? null : new StandardMethodContract(newState, returnValue);
  }

  private List<StandardMethodContract> visitEqualityComparison(List<ValueConstraint[]> states,
                                                               LighterASTNode op1,
                                                               LighterASTNode op2,
                                                               boolean equality) {
    int parameter = resolveParameter(op1);
    ValueConstraint constraint = getLiteralConstraint(op2);
    if (parameter < 0 || constraint == null) {
      parameter = resolveParameter(op2);
      constraint = getLiteralConstraint(op1);
    }
    if (parameter >= 0 && constraint != null) {
      List<StandardMethodContract> result = ContainerUtil.newArrayList();
      for (ValueConstraint[] state : states) {
        if (constraint == NOT_NULL_VALUE) {
          if (getPrimitiveParameterType(parameter) == null) {
            ContainerUtil.addIfNotNull(result, contractWithConstraint(state, parameter, NULL_VALUE, returnBoolean(!equality)));
          }
        } else {
          ContainerUtil.addIfNotNull(result, contractWithConstraint(state, parameter, constraint, returnBoolean(equality)));
          ContainerUtil.addIfNotNull(result, contractWithConstraint(state, parameter, constraint.negate(), returnBoolean(!equality)));
        }
      }
      return result;
    }
    return emptyList();
  }

  @Nullable
  private IElementType getPrimitiveParameterType(int paramIndex) {
    LighterASTNode typeElement = firstChildOfType(myTree, getParameters().get(paramIndex), TYPE);
    LighterASTNode primitive = firstChildOfType(myTree, typeElement, ElementType.PRIMITIVE_TYPE_BIT_SET);
    return primitive == null ? null : primitive.getTokenType();
  }

  static List<StandardMethodContract> toContracts(List<ValueConstraint[]> states, ContractReturnValue constraint) {
    return ContainerUtil.map(states, state -> new StandardMethodContract(state, constraint));
  }

  private List<StandardMethodContract> visitLogicalOperation(List<LighterASTNode> operands, boolean conjunction, List<ValueConstraint[]> states) {
    BooleanReturnValue breakValue = returnBoolean(!conjunction);
    List<StandardMethodContract> finalStates = ContainerUtil.newArrayList();
    for (LighterASTNode operand : operands) {
      List<PreContract> opResults = visitExpression(states, operand);
      finalStates.addAll(ContainerUtil.filter(knownContracts(opResults), contract -> contract.getReturnValue() == breakValue));
      states = antecedentsReturning(opResults, breakValue.negate());
    }
    finalStates.addAll(toContracts(states, breakValue.negate()));
    return finalStates;
  }

  private static List<StandardMethodContract> knownContracts(List<PreContract> values) {
    return ContainerUtil.mapNotNull(values, pc -> pc instanceof KnownContract ? ((KnownContract)pc).getContract() : null);
  }

  private static List<ValueConstraint[]> antecedentsReturning(List<PreContract> values, ContractReturnValue result) {
    return ContainerUtil.mapNotNull(knownContracts(values),
                                    contract -> contract.getReturnValue().equals(result) ?
                                                contract.getConstraints().toArray(new ValueConstraint[0]) : null);
  }

  private static class CodeBlockContracts {
    List<PreContract> accumulated = new ArrayList<>();
    List<ExpressionRange> varInitializers = new ArrayList<>();

    void addAll(List<PreContract> contracts) {
      if (contracts.isEmpty()) return;

      if (varInitializers.isEmpty()) {
        accumulated.addAll(contracts);
      } else {
        accumulated.add(new SideEffectFilter(varInitializers, contracts));
      }
    }

    void registerDeclaration(@NotNull LighterASTNode declStatement, @NotNull LighterAST tree, int scopeStart) {
      for (LighterASTNode var : getChildrenOfType(tree, declStatement, LOCAL_VARIABLE)) {
        LighterASTNode initializer = findExpressionChild(tree, var);
        if (initializer != null) {
          varInitializers.add(ExpressionRange.create(initializer, scopeStart));
        }
      }
    }
  }

  @NotNull
  private List<PreContract> visitStatements(List<ValueConstraint[]> states, List<LighterASTNode> statements) {
    CodeBlockContracts result = new CodeBlockContracts();
    for (LighterASTNode statement : statements) {
      IElementType type = statement.getTokenType();
      if (type == BLOCK_STATEMENT) {
        result.addAll(visitStatements(states, getStatements(getCodeBlock(statement), myTree)));
      }
      else if (type == IF_STATEMENT) {
        List<PreContract> conditionResults = visitExpression(states, findExpressionChild(myTree, statement));

        List<LighterASTNode> thenElse = getStatements(statement, myTree);
        if (thenElse.size() > 0) {
          result.addAll(visitStatements(antecedentsReturning(conditionResults, returnTrue()), singletonList(thenElse.get(0))));
        }

        List<ValueConstraint[]> falseStates = antecedentsReturning(conditionResults, returnFalse());
        if (thenElse.size() > 1) {
          result.addAll(visitStatements(falseStates, singletonList(thenElse.get(1))));
        } else {
          states = falseStates;
          continue;
        }
      }
      else if (type == WHILE_STATEMENT) {
        states = antecedentsReturning(visitExpression(states, findExpressionChild(myTree, statement)), returnFalse());
        continue;
      }
      else if (type == THROW_STATEMENT) {
        result.addAll(asPreContracts(toContracts(states, fail())));
      }
      else if (type == RETURN_STATEMENT) {
        result.addAll(visitExpression(states, findExpressionChild(myTree, statement)));
      }
      else if (type == ASSERT_STATEMENT) {
        List<PreContract> conditionResults = visitExpression(states, findExpressionChild(myTree, statement));
        result.addAll(asPreContracts(toContracts(antecedentsReturning(conditionResults, returnFalse()), fail())));
      }
      else if (type == DECLARATION_STATEMENT) {
        result.registerDeclaration(statement, myTree, myBody.getStartOffset());
        continue;
      }
      else if (type == DO_WHILE_STATEMENT) {
        result.addAll(visitStatements(states, getStatements(statement, myTree)));
      }

      break; // visit only the first statement unless it's 'if' whose 'then' always returns and the next statement is effectively 'else'
    }
    return result.accumulated;
  }

  @Nullable
  private ValueConstraint getLiteralConstraint(@Nullable LighterASTNode expr) {
    if (expr != null && expr.getTokenType() == LITERAL_EXPRESSION) {
      return getLiteralConstraint(myTree.getChildren(expr).get(0).getTokenType());
    }
    return null;
  }

  @NotNull
  static ValueConstraint getLiteralConstraint(@NotNull IElementType literalTokenType) {
    if (literalTokenType.equals(JavaTokenType.TRUE_KEYWORD)) return TRUE_VALUE;
    if (literalTokenType.equals(JavaTokenType.FALSE_KEYWORD)) return FALSE_VALUE;
    if (literalTokenType.equals(JavaTokenType.NULL_KEYWORD)) return NULL_VALUE;
    return NOT_NULL_VALUE;
  }

  private int resolveParameter(@Nullable LighterASTNode expr) {
    if (expr != null && expr.getTokenType() == REFERENCE_EXPRESSION && findExpressionChild(myTree, expr) == null) {
      String name = getNameIdentifierText(myTree, expr);
      if (name == null) return -1;

      List<LighterASTNode> parameters = getParameters();
      for (int i = 0; i < parameters.size(); i++) {
        if (name.equals(getNameIdentifierText(myTree, parameters.get(i)))) {
          return i;
        }
      }
    }
    return -1;
  }

  @Nullable
  static ValueConstraint[] withConstraint(ValueConstraint[] constraints, int index, ValueConstraint constraint) {
    if (constraints[index] == constraint) return constraints;

    ValueConstraint negated = constraint.negate();
    if (negated != constraint && constraints[index] == negated) {
      return null;
    }

    ValueConstraint[] copy = constraints.clone();
    copy[index] = constraint;
    return copy;
  }

}