/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.psi.PsiElement;
import com.sun.jdi.Value;

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
   * @param context
   */
  PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException;

  boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor);
}
