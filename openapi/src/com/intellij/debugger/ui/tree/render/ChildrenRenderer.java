package com.intellij.debugger.ui.tree.render;

import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.DebuggerContext;
import com.sun.jdi.Value;
import com.sun.tools.corba.se.idl.constExpr.EvaluationException;

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
  PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException;

  boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor);
}
