// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference;

import com.intellij.codeInsight.Nullability;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.FileLocalResolver;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.impl.source.JavaLightTreeUtil.*;
import static com.intellij.psi.impl.source.tree.JavaElementType.*;
import static com.intellij.psi.impl.source.tree.LightTreeUtil.firstChildOfType;
import static com.intellij.psi.impl.source.tree.LightTreeUtil.getChildrenOfType;

class MethodReturnInferenceVisitor {
  private static final TokenSet SHORT_CIRCUIT = TokenSet.create(JavaTokenType.ANDAND, JavaTokenType.OROR);
  private final LighterAST tree;
  private final List<LighterASTNode> myParameters;
  private final LighterASTNode myBody;
  private boolean hasErrors;
  private ReturnValue myReturnValue = ReturnValue.TOP;
  private boolean hasSystemExit;

  MethodReturnInferenceVisitor(LighterAST tree, List<LighterASTNode> parameters, LighterASTNode body) {
    this.tree = tree;
    myParameters = parameters;
    myBody = body;
  }

  void visitNode(LighterASTNode element) {
    IElementType type = element.getTokenType();

    if (type == TokenType.ERROR_ELEMENT) {
      hasErrors = true;
    }
    else if (type == RETURN_STATEMENT) {
      LighterASTNode value = findExpressionChild(tree, element);
      if (value == null) {
        hasErrors= true;
      } else {
        myReturnValue = ReturnValue.merge(myReturnValue, getExpressionValue(value));
      }
    }
    else if (type == METHOD_CALL_EXPRESSION) {
      LighterASTNode reference = findExpressionChild(tree, element);
      if ("exit".equals(getNameIdentifierText(tree, reference))) {
        LighterASTNode qualifier = findExpressionChild(tree, reference);
        if ("System".equals(getNameIdentifierText(tree, qualifier)) && findExpressionChild(tree, qualifier) == null) {
          hasSystemExit = true;
        }
      }
    }
  }

  @NotNull
  private ReturnValue getExpressionValue(@Nullable LighterASTNode expr) {
    expr = skipParenthesesCastsDown(tree, expr);
    if (expr == null) {
      return ReturnValue.UNKNOWN;
    }
    IElementType type = expr.getTokenType();
    if (isNullLiteral(expr)) {
      return ReturnValue.NULLABLE;
    }
    if (type == LAMBDA_EXPRESSION || type == NEW_EXPRESSION || type == METHOD_REF_EXPRESSION ||
             type == LITERAL_EXPRESSION || type == BINARY_EXPRESSION || type == POLYADIC_EXPRESSION) {
      return ReturnValue.NOT_NULL;
    }
    if (type == METHOD_CALL_EXPRESSION) {
      String calledMethod = getNameIdentifierText(tree, tree.getChildren(expr).get(0));
      if (calledMethod != null) {
        return ReturnValue.delegate(calledMethod, ExpressionRange.create(expr, myBody.getStartOffset()));
      }
    }
    else if (type == CONDITIONAL_EXPRESSION) {
      List<LighterASTNode> expressionChildren = getExpressionChildren(tree, expr);
      if(expressionChildren.size() == 3) {
        return ReturnValue.merge(getExpressionValue(expressionChildren.get(1)), // then-branch
                                 getExpressionValue(expressionChildren.get(2))); // else-branch
      }
    }
    else if (type == REFERENCE_EXPRESSION) {
      LighterASTNode target = new FileLocalResolver(tree).resolveLocally(expr).getTarget();
      if (target != null &&
          (target.getTokenType() == LOCAL_VARIABLE || target.getTokenType() == PARAMETER)) {
        return findVariableValue(expr, target);
      }
    }
    return ReturnValue.UNKNOWN;
  }

  @NotNull
  private ReturnValue findVariableValue(LighterASTNode expr, LighterASTNode target) {
    LighterASTNode parent;
    while (true) {
      parent = tree.getParent(expr);
      if (parent == null) {
        return ReturnValue.UNKNOWN;
      }
      IElementType type = parent.getTokenType();
      if (!ElementType.EXPRESSION_BIT_SET.contains(type)) break;
      if (type == CONDITIONAL_EXPRESSION) {
        List<LighterASTNode> operands = getExpressionChildren(tree, parent);
        if (operands.size() == 3) {
          LighterASTNode condition = operands.get(0);
          if (expr.equals(operands.get(1)) && isNullCheck(condition, target, false) ||
              expr.equals(operands.get(2)) && isNullCheck(condition, target, true)) {
            return ReturnValue.NOT_NULL;
          }
        }
      }
      expr = parent;
    }
    if (ElementType.JAVA_STATEMENT_BIT_SET.contains(parent.getTokenType())) {
      ReturnValue value = findValueBeforeStatement(parent, target);
      if (!myParameters.isEmpty()) {
        // For now disable NULLABLE inference (except delegation case), because contract inference
        // is not so smart one and we may infer NULLABLE where contract is possible producing noise
        // nullability warnings
        value = value.dropNullable();
      }
      return value;
    }
    return ReturnValue.UNKNOWN;
  }

  @NotNull
  private ReturnValue findValueBeforeStatement(LighterASTNode statement, LighterASTNode target) {
    LighterASTNode parent = tree.getParent(statement);
    if (parent == null) {
      return ReturnValue.UNKNOWN;
    }
    if (parent.getTokenType() == CODE_BLOCK) {
      List<LighterASTNode> children = tree.getChildren(parent);
      for (int i = children.lastIndexOf(statement) - 1; i >= 0; i--) {
        LighterASTNode child = children.get(i);
        if (ElementType.JAVA_STATEMENT_BIT_SET.contains(child.getTokenType())) {
          ReturnValue value = findValueInStatement(child, target);
          if (value != null) {
            return value;
          }
        }
      }
      LighterASTNode grandParent = tree.getParent(parent);
      if (grandParent == null || grandParent.getTokenType() != BLOCK_STATEMENT) return ReturnValue.UNKNOWN;
      return findValueBeforeStatement(grandParent, target);
    }
    if (parent.getTokenType() == IF_STATEMENT) {
      LighterASTNode condition = findExpressionChild(tree, parent);
      List<LighterASTNode> branches = getChildrenOfType(tree, parent, ElementType.JAVA_STATEMENT_BIT_SET);
      int index = branches.indexOf(statement);
      if (index == 0 && isNullCheck(condition, target, false) || index == 1 && isNullCheck(condition, target, true)) {
        return ReturnValue.NOT_NULL;
      }
      ReturnValue value = findValueInExpression(condition, target);
      if (value != null) {
        return value;
      }
      return findValueBeforeStatement(parent, target);
    }
    if (parent.getTokenType() == WHILE_STATEMENT) {
      LighterASTNode condition = findExpressionChild(tree, parent);
      if (isNullCheck(condition, target, false)) {
        return ReturnValue.NOT_NULL;
      }
      // Could be reassigned later in loop, do not analyze this now
      return ReturnValue.UNKNOWN;
    }
    if (parent.getTokenType() == SYNCHRONIZED_STATEMENT) {
      ReturnValue value = findValueInExpression(findExpressionChild(tree, parent), target);
      if (value != null) {
        return value;
      }
      return findValueBeforeStatement(parent, target);
    }
    return ReturnValue.UNKNOWN;
  }

  @Nullable
  private ReturnValue findValueInStatement(LighterASTNode statement, LighterASTNode target) {
    if (statement == null) return null;
    IElementType tokenType = statement.getTokenType();
    if (tokenType == EXPRESSION_STATEMENT) {
      LighterASTNode expression = findExpressionChild(tree, statement);
      return findValueInExpression(expression, target);
    }
    if (tokenType == DECLARATION_STATEMENT) {
      List<LighterASTNode> declaredElements = tree.getChildren(statement);
      for (int i = declaredElements.size() - 1; i >= 0; i--) {
        LighterASTNode declared = declaredElements.get(i);
        if (declared.getTokenType() != LOCAL_VARIABLE) continue;
        LighterASTNode initializer = findExpressionChild(tree, declared);
        if (declared.equals(target)) {
          return getExpressionValue(initializer);
        }
        ReturnValue value = findValueInExpression(initializer, target);
        if (value != null) {
          return value;
        }
      }
    }
    if (tokenType == BLOCK_STATEMENT) {
      LighterASTNode block = firstChildOfType(tree, statement, CODE_BLOCK);
      if (block != null) {
        List<LighterASTNode> children = getChildrenOfType(tree, block, ElementType.JAVA_STATEMENT_BIT_SET);
        for (int i = children.size() - 1; i >= 0; i--) {
          ReturnValue value = findValueInStatement(children.get(i), target);
          if (value != null) {
            return value;
          }
        }
      }
      return null;
    }
    if (tokenType == IF_STATEMENT) {
      ReturnValue value = findValueInIfStatement(statement, target);
      if (value != null) {
        return value;
      }
    }
    if (isAssignedInside(statement, target)) return ReturnValue.UNKNOWN;
    return null;
  }

  @Nullable
  private ReturnValue findValueInIfStatement(LighterASTNode statement, LighterASTNode target) {
    List<LighterASTNode> branches = getChildrenOfType(tree, statement, ElementType.JAVA_STATEMENT_BIT_SET);
    LighterASTNode condition = findExpressionChild(tree, statement);
    LighterASTNode thenBranch = ContainerUtil.getFirstItem(branches);
    LighterASTNode elseBranch = branches.size() == 2 ? branches.get(1) : null;
    boolean thenBreaks = completesAbruptly(thenBranch);
    boolean elseBreaks = completesAbruptly(elseBranch);
    ReturnValue thenValue = findValueInStatement(thenBranch, target);
    ReturnValue elseValue = findValueInStatement(elseBranch, target);
    if (elseValue == null && thenBreaks && !elseBreaks && isNullCheck(condition, target, true)) {
      elseValue = ReturnValue.NOT_NULL;
    }
    if (thenValue == null && elseBreaks && !thenBreaks && isNullCheck(condition, target, false)) {
      thenValue = ReturnValue.NOT_NULL;
    }
    if (thenBreaks && !elseBreaks) {
      thenValue = elseValue;
    }
    if (elseBreaks && !thenBreaks) {
      elseValue = thenValue;
    }

    if (thenValue == null) {
      thenValue =
        isNullCheck(condition, target, false) ? ReturnValue.NOT_NULL : findValueInExpression(condition, target);
    }
    if (elseValue == null) {
      elseValue =
        isNullCheck(condition, target, true) ? ReturnValue.NOT_NULL : findValueInExpression(condition, target);
    }

    if (thenValue == null || elseValue == null) {
      return null;
    }
    return ReturnValue.merge(thenValue, elseValue);
  }

  @Nullable
  private ReturnValue findValueInExpression(LighterASTNode expression, LighterASTNode target) {
    if (expression == null) return null;
    IElementType type = expression.getTokenType();
    if (type == ASSIGNMENT_EXPRESSION) {
      List<LighterASTNode> children = getExpressionChildren(tree, expression);
      if (children.size() == 2 && isReferenceToLocal(children.get(0), target)) {
        if (firstChildOfType(tree, expression, JavaTokenType.EQ) == null) {
          // compound assignment always produces non-null
          return ReturnValue.NOT_NULL;
        }
        return getExpressionValue(children.get(1));
      }
    }
    if (isAssignedInside(expression, target)) {
      return ReturnValue.UNKNOWN;
    }
    if (isDereferencedInside(expression, target)) {
      return ReturnValue.NOT_NULL;
    }
    return null;
  }

  private boolean completesAbruptly(LighterASTNode statement) {
    if (statement == null) return false;
    if (statement.getTokenType() == BLOCK_STATEMENT) {
      LighterASTNode block = firstChildOfType(tree, statement, CODE_BLOCK);
      if (block != null) {
        LighterASTNode lastStatement = ContainerUtil.getLastItem(getChildrenOfType(tree, block, ElementType.JAVA_STATEMENT_BIT_SET));
        return lastStatement != null && completesAbruptly(lastStatement);
      }
    }
    if (statement.getTokenType() == BREAK_STATEMENT || statement.getTokenType() == CONTINUE_STATEMENT ||
        statement.getTokenType() == RETURN_STATEMENT || statement.getTokenType() == THROW_STATEMENT) {
      return true;
    }
    return false;
  }

  private boolean isAssignedInside(LighterASTNode element, LighterASTNode target) {
    Queue<LighterASTNode> workList = new ArrayDeque<>();
    workList.add(element);
    while(!workList.isEmpty()) {
      LighterASTNode node = workList.poll();
      if (isReferenceToLocal(node, target)) {
        LighterASTNode parent = tree.getParent(node);
        if (parent != null) {
          IElementType type = parent.getTokenType();
          if (type == ASSIGNMENT_EXPRESSION && node.equals(findExpressionChild(tree, parent))) return true;
          if (type == POSTFIX_EXPRESSION || type == PREFIX_EXPRESSION) return true;
        }
      }
      IElementType tokenType = node.getTokenType();
      if (tokenType != LAMBDA_EXPRESSION && tokenType != TYPE_PARAMETER_LIST && tokenType != ANONYMOUS_CLASS) {
        workList.addAll(tree.getChildren(node));
      }
    }
    return false;
  }

  private boolean isDereferencedInside(LighterASTNode expression, LighterASTNode target) {
    Queue<LighterASTNode> workList = new ArrayDeque<>();
    workList.add(expression);
    while(!workList.isEmpty()) {
      LighterASTNode node = workList.poll();
      if (isReferenceToLocal(node, target)) {
        LighterASTNode parent = tree.getParent(node);
        if (parent != null) {
          IElementType type = parent.getTokenType();
          if (type == REFERENCE_EXPRESSION || type == ARRAY_ACCESS_EXPRESSION) return true;
        }
      }
      IElementType tokenType = node.getTokenType();
      if (tokenType == CONDITIONAL_EXPRESSION || tokenType == SWITCH_EXPRESSION ||
          (tokenType == POLYADIC_EXPRESSION || tokenType == BINARY_EXPRESSION) && firstChildOfType(tree, node, SHORT_CIRCUIT) != null) {
        ContainerUtil.addIfNotNull(workList, findExpressionChild(tree, node));
      } else if (tokenType != LAMBDA_EXPRESSION && tokenType != TYPE_PARAMETER_LIST && tokenType != ANONYMOUS_CLASS) {
        workList.addAll(tree.getChildren(node));
      }
    }
    return false;
  }

  private boolean isNullCheck(@Nullable LighterASTNode expr, LighterASTNode var, boolean negated) {
    expr = skipParenthesesCastsDown(tree, expr);
    if (expr == null || isAssignedInside(expr, var)) return false;

    IElementType type = expr.getTokenType();
    if (type == BINARY_EXPRESSION || type == POLYADIC_EXPRESSION) {
      List<LighterASTNode> operands = getExpressionChildren(tree, expr);
      if (firstChildOfType(tree, expr, negated ? JavaTokenType.EQEQ : JavaTokenType.NE) != null) {
        return operands.size() == 2 && isNullLiteral(operands.get(1)) && isReferenceToLocal(operands.get(0), var);
      }

      if (firstChildOfType(tree, expr, negated ? JavaTokenType.OROR : JavaTokenType.ANDAND) == null) return false;
      for (LighterASTNode t : operands) {
        if (isNullCheck(t, var, negated)) return true;
      }
      return false;
    }
    if (type == PREFIX_EXPRESSION && firstChildOfType(tree, expr, JavaTokenType.EXCL) != null) {
      return isNullCheck(findExpressionChild(tree, expr), var, !negated);
    }

    return !negated && type == INSTANCE_OF_EXPRESSION && isReferenceToLocal(findExpressionChild(tree, expr), var);
  }

  private boolean isReferenceToLocal(@Nullable LighterASTNode operand, @NotNull LighterASTNode var) {
    // We do not actually resolve here as this method is guaranteed to be called within the scope of var,
    // thus simply comparing names is enough
    return operand != null &&
           operand.getTokenType() == REFERENCE_EXPRESSION &&
           Objects.requireNonNull(tree.getParent(operand)).getTokenType() != METHOD_CALL_EXPRESSION &&
           findExpressionChild(tree, operand) == null && // non-qualified
           Objects.equals(getNameIdentifierText(tree, operand), getNameIdentifierText(tree, var));
  }

  private boolean isNullLiteral(@NotNull LighterASTNode value) {
    return value.getTokenType() == LITERAL_EXPRESSION && tree.getChildren(value).get(0).getTokenType() == JavaTokenType.NULL_KEYWORD;
  }

  @Nullable
  MethodReturnInferenceResult getResult() {
    List<ExpressionRange> delegateCalls = myReturnValue.myCalledMethod == null ? null : myReturnValue.myRanges;
    boolean hasNulls = myReturnValue.myNullability.contains(Nullability.NULLABLE);
    boolean hasNotNulls = myReturnValue.myNullability.contains(Nullability.NOT_NULL);
    boolean hasUnknowns = myReturnValue.myNullability.contains(Nullability.UNKNOWN);
    if (hasNulls) {
      if (hasSystemExit) {
        return new MethodReturnInferenceResult.Predefined(Nullability.UNKNOWN);
      }
      return delegateCalls == null || hasNotNulls || hasErrors || hasUnknowns
             ? new MethodReturnInferenceResult.Predefined(Nullability.NULLABLE)
             : new MethodReturnInferenceResult.FromDelegate(Nullability.NULLABLE, delegateCalls);
    }
    if (hasErrors || hasUnknowns) {
      return null;
    }
    if (delegateCalls != null) {
      return new MethodReturnInferenceResult.FromDelegate(hasNotNulls ? Nullability.NOT_NULL : Nullability.UNKNOWN, delegateCalls);
    }

    if (hasNotNulls) {
      return new MethodReturnInferenceResult.Predefined(Nullability.NOT_NULL);
    }
    return null;
  }

  private static final class ReturnValue {
    static final ReturnValue TOP = new ReturnValue(EnumSet.noneOf(Nullability.class), null, Collections.emptyList());
    static final ReturnValue UNKNOWN = new ReturnValue(EnumSet.of(Nullability.UNKNOWN), null, Collections.emptyList());
    static final ReturnValue NULLABLE = new ReturnValue(EnumSet.of(Nullability.NULLABLE), null, Collections.emptyList());
    static final ReturnValue NOT_NULL = new ReturnValue(EnumSet.of(Nullability.NOT_NULL), null, Collections.emptyList());

    final @NotNull EnumSet<Nullability> myNullability; // empty = top
    final @Nullable String myCalledMethod; // null = top; empty = bottom
    final @NotNull List<ExpressionRange> myRanges;

    private ReturnValue(@NotNull EnumSet<Nullability> nullability,
                        @Nullable String method,
                        @NotNull List<ExpressionRange> ranges) {
      myNullability = nullability;
      myCalledMethod = method;
      myRanges = ranges;
    }

    public ReturnValue dropNullable() {
      if (!myNullability.contains(Nullability.NULLABLE)) return this;
      EnumSet<Nullability> copy = EnumSet.copyOf(myNullability);
      copy.remove(Nullability.NULLABLE);
      copy.add(Nullability.UNKNOWN);
      return new ReturnValue(copy, myCalledMethod, myRanges);
    }

    static ReturnValue delegate(@NotNull String method, @NotNull ExpressionRange range) {
      return new ReturnValue(EnumSet.noneOf(Nullability.class), method, Collections.singletonList(range));
    }

    static ReturnValue merge(@NotNull ReturnValue left, @NotNull ReturnValue right) {
      EnumSet<Nullability> nullability = EnumSet.copyOf(left.myNullability);
      nullability.addAll(right.myNullability);
      String calledMethod;
      List<ExpressionRange> range;
      if (left.myCalledMethod == null) {
        calledMethod = right.myCalledMethod;
        range = right.myRanges;
      }
      else if (right.myCalledMethod == null) {
        calledMethod = left.myCalledMethod;
        range = left.myRanges;
      }
      else if (left.myCalledMethod.equals(right.myCalledMethod)) {
        calledMethod = left.myCalledMethod;
        range = ContainerUtil.concat(left.myRanges, right.myRanges);
      }
      else {
        calledMethod = "";
        range = Collections.emptyList();
        nullability.add(Nullability.UNKNOWN);
      }
      return new ReturnValue(nullability, calledMethod, range);
    }
  }
}
