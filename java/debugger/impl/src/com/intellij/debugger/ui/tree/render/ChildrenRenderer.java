// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.psi.PsiElement;
import com.sun.jdi.Value;

import java.util.concurrent.CompletableFuture;

public interface ChildrenRenderer extends Renderer {
  void buildChildren(Value value, ChildrenBuilder  builder, EvaluationContext evaluationContext);

  /**
   * - parentNode
   *    + ..
   *    + node
   *    + ...
   *
   * is invoked on the renderer of the parentNode
   * @param node a child node
   * @return expression that evaluates the child node.
   *         Use 'this' to refer the expression that evaluates this (parent) node
   */
  PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException;

  /**
   * @deprecated override {@link #isExpandableAsync(Value, EvaluationContext, NodeDescriptor)}
   */
  @Deprecated
  default boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    throw new AbstractMethodError("isExpandableAsync is not implemented");
  }

  default CompletableFuture<Boolean> isExpandableAsync(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return CompletableFuture.completedFuture(isExpandable(value, evaluationContext, parentDescriptor));
  }
}
