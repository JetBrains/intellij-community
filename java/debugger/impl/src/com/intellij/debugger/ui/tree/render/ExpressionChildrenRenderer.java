package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.NodeManager;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * User: lex
 * Date: Sep 17, 2003
 * Time: 2:04:00 PM
 */
public class ExpressionChildrenRenderer extends ReferenceRenderer implements ChildrenRenderer {
  public static final @NonNls String UNIQUE_ID = "ExpressionChildrenRenderer";
  private static final Key<Value> EXPRESSION_VALUE = new Key<Value>("EXPRESSION_VALUE");

  private final CachedEvaluator myChildrenExpandable = new CachedEvaluator() {
    protected String getClassName() {
      return ExpressionChildrenRenderer.this.getClassName();
    }
  };

  private final CachedEvaluator myChildrenExpression = new CachedEvaluator() {
    protected String getClassName() {
      return ExpressionChildrenRenderer.this.getClassName();
    }
  };

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public ExpressionChildrenRenderer clone() {
    return (ExpressionChildrenRenderer)super.clone();
  }

  public void buildChildren(final Value value, final ChildrenBuilder builder, final EvaluationContext evaluationContext) {
    final NodeManager nodeManager = builder.getNodeManager();

    try {
      final Value childrenValue = evaluateChildren(
        evaluationContext.createEvaluationContext(value), builder.getParentDescriptor()
      );

      final ChildrenRenderer defaultRenderer = ((DebugProcessImpl)evaluationContext.getDebugProcess()).getDefaultRenderer(childrenValue != null? childrenValue.type() : null);
      defaultRenderer.buildChildren(childrenValue, builder, evaluationContext);
    }
    catch (final EvaluateException e) {
      List<DebuggerTreeNode> errorChildren = new ArrayList<DebuggerTreeNode>();
      errorChildren.add(nodeManager.createMessageNode(DebuggerBundle.message("error.unable.to.evaluate.expression") + " " + e.getMessage()));
      builder.setChildren(errorChildren);
    }
  }

  private Value evaluateChildren(EvaluationContext context, NodeDescriptor descriptor) throws EvaluateException {
    final ExpressionEvaluator evaluator = myChildrenExpression.getEvaluator(context.getProject());

    Value value = evaluator.evaluate(context);
    descriptor.putUserData(EXPRESSION_VALUE, value);
    return value;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);

    TextWithImports childrenExpression = DebuggerUtils.getInstance().readTextWithImports(element, "CHILDREN_EXPRESSION");
    if(childrenExpression != null) {
      setChildrenExpression(childrenExpression);
    }

    TextWithImports childrenExpandable = DebuggerUtils.getInstance().readTextWithImports(element, "CHILDREN_EXPANDABLE");
    if(childrenExpandable != null) {
      myChildrenExpandable.setReferenceExpression(childrenExpandable);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
    DebuggerUtils.getInstance().writeTextWithImports(element, "CHILDREN_EXPANDABLE", getChildrenExpandable());
    DebuggerUtils.getInstance().writeTextWithImports(element, "CHILDREN_EXPRESSION", getChildrenExpression());
  }

  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    ValueDescriptor descriptor = (ValueDescriptor) node.getParent().getDescriptor();
    Value expressionValue = descriptor.getUserData(EXPRESSION_VALUE);
    if(expressionValue == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("error.unable.to.evaluate.expression"));
    }

    ChildrenRenderer defaultChildrenRenderer = ((DebugProcessImpl)context.getDebugProcess()).getDefaultRenderer(expressionValue.type());

    return DebuggerUtils.getInstance().substituteThis(
            defaultChildrenRenderer.getChildValueExpression(node, context),
            (PsiExpression)myChildrenExpression.getPsiExpression(node.getProject()).copy(),
            expressionValue, context);
  }

  public boolean isExpandable(Value value, final EvaluationContext context, NodeDescriptor parentDescriptor) {
    final EvaluationContext evaluationContext = context.createEvaluationContext(value);

    if(!"".equals(myChildrenExpandable.getReferenceExpression().getText())) {
      try {
        Value expanded = myChildrenExpandable.getEvaluator(evaluationContext.getProject()).evaluate(evaluationContext);
        if(expanded instanceof BooleanValue) {
          return ((BooleanValue)expanded).booleanValue();
        }
      }
      catch (EvaluateException e) {
        // ignored
      }
    }

    try {
      Value children = evaluateChildren(evaluationContext, parentDescriptor);

      ChildrenRenderer defaultChildrenRenderer = ((DebugProcessImpl)evaluationContext.getDebugProcess()).getDefaultRenderer(value.type());

      return defaultChildrenRenderer.isExpandable(children, evaluationContext, parentDescriptor);
    }
    catch (EvaluateException e) {
      return true;
    }
  }

  public TextWithImports getChildrenExpression() {
    return myChildrenExpression.getReferenceExpression();
  }

  public void setChildrenExpression(TextWithImports expression) {
    myChildrenExpression.setReferenceExpression(expression);
  }

  public TextWithImports getChildrenExpandable() {
    return myChildrenExpandable.getReferenceExpression();
  }

  public void setChildrenExpandable(TextWithImports childrenExpandable) {
    myChildrenExpandable.setReferenceExpression(childrenExpandable);
  }

  public void setClassName(String name) {
    super.setClassName(name);
    myChildrenExpression.clear();
    myChildrenExpandable.clear();
  }

}
