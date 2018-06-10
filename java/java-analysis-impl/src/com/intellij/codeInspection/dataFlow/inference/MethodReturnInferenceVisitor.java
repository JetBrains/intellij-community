// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference;

import com.intellij.codeInsight.Nullability;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.FileLocalResolver;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.intellij.psi.impl.source.JavaLightTreeUtil.*;
import static com.intellij.psi.impl.source.tree.JavaElementType.*;
import static com.intellij.psi.impl.source.tree.LightTreeUtil.firstChildOfType;

class MethodReturnInferenceVisitor {
  private final LighterAST tree;
  private final LighterASTNode body;
  private boolean hasErrors;
  private boolean hasNotNulls;
  private boolean hasNulls;
  private boolean hasUnknowns;
  private boolean hasSystemExit;
  MultiMap<String, ExpressionRange> delegates = MultiMap.create();
  Set<String> assignments = ContainerUtil.newHashSet();
  Set<String> returnedCheckedVars = ContainerUtil.newHashSet();

  MethodReturnInferenceVisitor(LighterAST tree, LighterASTNode body) {
    this.tree = tree;
    this.body = body;
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
        visitReturnedValue(value);
      }
    }
    else if (type == ASSIGNMENT_EXPRESSION) {
      ContainerUtil.addIfNotNull(assignments, getNameIdentifierText(tree, findExpressionChild(tree, element)));
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

  private void visitReturnedValue(@Nullable LighterASTNode expr) {
    expr = skipParenthesesCastsDown(tree, expr);
    if (expr == null) {
      hasErrors = true;
      return;
    }
    IElementType type = expr.getTokenType();
    if (isNullLiteral(expr)) {
      hasNulls = true;
    }
    else if (type == LAMBDA_EXPRESSION || type == NEW_EXPRESSION || type == METHOD_REF_EXPRESSION ||
             type == LITERAL_EXPRESSION || type == BINARY_EXPRESSION || type == POLYADIC_EXPRESSION) {
      hasNotNulls = true;
    }
    else if (type == METHOD_CALL_EXPRESSION) {
      String calledMethod = getNameIdentifierText(tree, tree.getChildren(expr).get(0));
      if (calledMethod != null) {
        delegates.putValue(calledMethod, ExpressionRange.create(expr, body.getStartOffset()));
      }
    }
    else if (type == CONDITIONAL_EXPRESSION) {
      List<LighterASTNode> expressionChildren = getExpressionChildren(tree, expr);
      if(expressionChildren.size() == 3) {
        visitReturnedValue(expressionChildren.get(1)); // then-branch
        visitReturnedValue(expressionChildren.get(2)); // else-branch
      } else {
        hasUnknowns = true;
      }
    }
    else if (type == REFERENCE_EXPRESSION) {
      LighterASTNode target = new FileLocalResolver(tree).resolveLocally(expr).getTarget();
      if (target != null &&
          (target.getTokenType() == LOCAL_VARIABLE || target.getTokenType() == PARAMETER) &&
          isCheckedForNotNull(target, expr)) {
        ContainerUtil.addIfNotNull(returnedCheckedVars, getNameIdentifierText(tree, target));
      } else {
        hasUnknowns = true;
      }
    }
    else {
      hasUnknowns = true;
    }
  }

  private boolean isCheckedForNotNull(LighterASTNode var, LighterASTNode ref) {
    JBIterable<LighterASTNode> hierarchy = JBIterable.generate(ref, tree::getParent);
    for (Pair<LighterASTNode, LighterASTNode> pair : ContainerUtil.zip(hierarchy, hierarchy.skip(1))) {
      LighterASTNode eachParent = pair.second;
      LighterASTNode prevParent = pair.first;
      IElementType type = eachParent.getTokenType();
      if (type == IF_STATEMENT &&
          prevParent.equals(ContainerUtil.getFirstItem(ContractInferenceInterpreter.getStatements(eachParent, tree))) &&
          isNonNullCondition(findExpressionChild(tree, eachParent), var)) {
        return true;
      }
      if (type == CONDITIONAL_EXPRESSION) {
        List<LighterASTNode> operands = getExpressionChildren(tree, eachParent);
        if (operands.size() == 3 && prevParent.equals(operands.get(1)) && isNonNullCondition(operands.get(0), var)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isNonNullCondition(@Nullable LighterASTNode expr, LighterASTNode var) {
    expr = skipParenthesesCastsDown(tree, expr);
    if (expr == null) return false;

    IElementType type = expr.getTokenType();
    if (type == BINARY_EXPRESSION || type == POLYADIC_EXPRESSION) {
      List<LighterASTNode> operands = getExpressionChildren(tree, expr);
      if (firstChildOfType(tree, expr, JavaTokenType.NE) != null) {
        return operands.size() == 2 && isNullLiteral(operands.get(1)) && isReferenceTo(operands.get(0), var);
      }

      return firstChildOfType(tree, expr, JavaTokenType.ANDAND) != null && ContainerUtil.exists(operands, e -> isNonNullCondition(e, var));
    }

    return type == INSTANCE_OF_EXPRESSION && isReferenceTo(expr, var);
  }

  private boolean isReferenceTo(@NotNull LighterASTNode expr, @NotNull LighterASTNode var) {
    LighterASTNode operand = skipParenthesesCastsDown(tree, findExpressionChild(tree, expr));
    if (operand == null ||
        operand.getTokenType() != REFERENCE_EXPRESSION ||
        !Objects.equals(getNameIdentifierText(tree, operand), getNameIdentifierText(tree, operand))) {
      return false;
    }
    return var.equals(new FileLocalResolver(tree).resolveLocally(operand).getTarget());
  }

  private boolean isNullLiteral(@NotNull LighterASTNode value) {
    return value.getTokenType() == LITERAL_EXPRESSION && tree.getChildren(value).get(0).getTokenType() == JavaTokenType.NULL_KEYWORD;
  }

  @Nullable
  MethodReturnInferenceResult getResult() {
    if (!returnedCheckedVars.isEmpty()) {
      if (ContainerUtil.exists(returnedCheckedVars, name -> !assignments.contains(name))) {
        hasNotNulls = true;
      } else {
        hasUnknowns = true;
      }
    }

    List<ExpressionRange> delegateCalls = null;
    if (delegates.size() == 1) {
      delegateCalls = ContainerUtil.newArrayList(delegates.get(delegates.keySet().iterator().next()));
    }
    if (hasNulls) {
      if (hasSystemExit) {
        return new MethodReturnInferenceResult.Predefined(Nullability.UNKNOWN);
      }
      return delegateCalls == null || hasNotNulls || hasErrors || hasUnknowns
             ? new MethodReturnInferenceResult.Predefined(Nullability.NULLABLE)
             : new MethodReturnInferenceResult.FromDelegate(Nullability.NULLABLE, delegateCalls);
    }
    if (hasErrors || hasUnknowns || delegates.size() > 1) {
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
}
