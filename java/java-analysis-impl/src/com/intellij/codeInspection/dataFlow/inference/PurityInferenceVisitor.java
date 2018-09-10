// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.FileLocalResolver;
import com.intellij.psi.impl.source.JavaLightTreeUtil;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.impl.source.tree.JavaElementType.*;

class PurityInferenceVisitor {
  private final LighterAST tree;
  private final LighterASTNode body;
  private final Set<String> myVolatileFieldNames;
  private final List<LighterASTNode> mutatedRefs = new ArrayList<>();
  private boolean hasReturns;
  private boolean hasVolatileReads;
  private final List<LighterASTNode> calls = new ArrayList<>();

  PurityInferenceVisitor(LighterAST tree, LighterASTNode body, Set<String> volatileFieldNames) {
    this.tree = tree;
    this.body = body;
    myVolatileFieldNames = volatileFieldNames;
  }

  void visitNode(LighterASTNode element) {
    IElementType type = element.getTokenType();
    if (type == ASSIGNMENT_EXPRESSION) {
      mutatedRefs.add(tree.getChildren(element).get(0));
    }
    else if (type == RETURN_STATEMENT && JavaLightTreeUtil.findExpressionChild(tree, element) != null) {
      hasReturns = true;
    }
    else if ((type == PREFIX_EXPRESSION || type == POSTFIX_EXPRESSION) && isMutatingOperation(element)) {
      ContainerUtil.addIfNotNull(mutatedRefs, JavaLightTreeUtil.findExpressionChild(tree, element));
    }
    else if (isCall(element, type)) {
      calls.add(element);
    }
    else if (type == REFERENCE_EXPRESSION && !myVolatileFieldNames.isEmpty()) {
      LighterASTNode qualifier = JavaLightTreeUtil.findExpressionChild(tree, element);
      if (qualifier == null || qualifier.getTokenType() == THIS_EXPRESSION) {
        if (myVolatileFieldNames.contains(JavaLightTreeUtil.getNameIdentifierText(tree, element))) {
          LighterASTNode target = new FileLocalResolver(tree).resolveLocally(element).getTarget();
          if (target != null && target.getTokenType() == FIELD &&
              JavaLightTreeUtil.hasExplicitModifier(tree, target, JavaTokenType.VOLATILE_KEYWORD)) {
            hasVolatileReads = true;
          }
        }
      }
    }
  }

  private boolean isCall(@NotNull LighterASTNode element, IElementType type) {
    return type == NEW_EXPRESSION && LightTreeUtil.firstChildOfType(tree, element, EXPRESSION_LIST) != null ||
           type == METHOD_CALL_EXPRESSION;
  }

  private boolean isMutatingOperation(@NotNull LighterASTNode element) {
    return LightTreeUtil.firstChildOfType(tree, element, JavaTokenType.PLUSPLUS) != null ||
           LightTreeUtil.firstChildOfType(tree, element, JavaTokenType.MINUSMINUS) != null;
  }

  @Nullable
  PurityInferenceResult getResult() {
    if (calls.size() > 1 || !hasReturns || hasVolatileReads) return null;

    int bodyStart = body.getStartOffset();
    return new PurityInferenceResult(ContainerUtil.map(mutatedRefs, node -> ExpressionRange.create(node, bodyStart)),
                                     calls.isEmpty() ? null : ExpressionRange.create(calls.get(0), bodyStart));
  }
}
