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

/**
 * User: lex
 * Date: Sep 20, 2003
 * Time: 10:12:01 PM
 */
public interface ChildrenRenderer extends Renderer {
  ChildrenRenderer clone();

  void buildChildren(Value value, ChildrenBuilder  builder, EvaluationContext evaluationContext);

  /**
   * - parentNode
   *    + ..
   *    + node
   *    + ...
   *
   * function is invoked for the renderer in parenNode
   * returns expression that evaluates node
   * use 'this' to refer expression that evaluate parentNode
   @param node  -
   * @param context
   */
  PsiExpression getChildrenValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException;

  boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor);
}
