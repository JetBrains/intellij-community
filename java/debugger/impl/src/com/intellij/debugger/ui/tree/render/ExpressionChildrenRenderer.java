// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.PossiblySyncCommand;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public final class ExpressionChildrenRenderer extends ReferenceRenderer implements ChildrenRenderer {
  public static final @NonNls String UNIQUE_ID = "ExpressionChildrenRenderer";
  private static final Key<Value> EXPRESSION_VALUE = new Key<>("EXPRESSION_VALUE");
  private static final Key<NodeRenderer> LAST_CHILDREN_RENDERER = new Key<>("LAST_CHILDREN_RENDERER");

  private CachedEvaluator myChildrenExpandable = createCachedEvaluator();
  private CachedEvaluator myChildrenExpression = createCachedEvaluator();

  private NodeRenderer myPredictedRenderer;

  @Override
  public String getUniqueId() {
    return UNIQUE_ID;
  }

  @Override
  public ExpressionChildrenRenderer clone() {
    ExpressionChildrenRenderer clone = (ExpressionChildrenRenderer)super.clone();
    clone.myChildrenExpandable = createCachedEvaluator();
    clone.setChildrenExpandable(getChildrenExpandable());
    clone.myChildrenExpression = createCachedEvaluator();
    clone.setChildrenExpression(getChildrenExpression());
    return clone;
  }

  @Override
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    EvaluationContextImpl evaluationContextImpl = (EvaluationContextImpl)evaluationContext;
    evaluationContextImpl.getManagerThread().schedule(new PossiblySyncCommand(evaluationContextImpl.getSuspendContext()) {
      @Override
      public void syncAction(@NotNull SuspendContextImpl suspendContext) {
        try {
          ValueDescriptor parentDescriptor = builder.getParentDescriptor();
          Value childrenValue = evaluateChildren(evaluationContext.createEvaluationContext(value), parentDescriptor);

          DebuggerUtilsAsync.type(childrenValue)
            .thenAccept(type -> getChildrenRenderer(type, parentDescriptor).buildChildren(childrenValue, builder, evaluationContext));
        }
        catch (EvaluateException e) {
          builder.setErrorMessage(JavaDebuggerBundle.message("error.unable.to.evaluate.children.expression") + " " + e.getMessage());
          // fallback to the default renderer
          DebugProcessImpl.getDefaultRenderer(value).buildChildren(value, builder, evaluationContext);
        }
      }
    });
  }

  @Nullable
  public static NodeRenderer getLastChildrenRenderer(ValueDescriptor descriptor) {
    return descriptor.getUserData(LAST_CHILDREN_RENDERER);
  }

  public static void setPreferableChildrenRenderer(ValueDescriptor descriptor, NodeRenderer renderer) {
    descriptor.putUserData(LAST_CHILDREN_RENDERER, renderer);
  }

  public static Value getLastChildrenValue(NodeDescriptor descriptor) {
    return descriptor.getUserData(EXPRESSION_VALUE);
  }

  private Value evaluateChildren(EvaluationContext context, NodeDescriptor descriptor) throws EvaluateException {
    ExpressionEvaluator evaluator = myChildrenExpression.getEvaluator(context.getProject());
    Value value = context.computeAndKeep(() -> evaluator.evaluate(context));
    descriptor.putUserData(EXPRESSION_VALUE, value);
    return value;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);

    TextWithImports childrenExpression = DebuggerUtils.getInstance().readTextWithImports(element, "CHILDREN_EXPRESSION");
    if (childrenExpression != null) {
      setChildrenExpression(childrenExpression);
    }

    TextWithImports childrenExpandable = DebuggerUtils.getInstance().readTextWithImports(element, "CHILDREN_EXPANDABLE");
    if (childrenExpandable != null) {
      myChildrenExpandable.setReferenceExpression(childrenExpandable);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
    DebuggerUtils.getInstance().writeTextWithImports(element, "CHILDREN_EXPANDABLE", getChildrenExpandable());
    DebuggerUtils.getInstance().writeTextWithImports(element, "CHILDREN_EXPRESSION", getChildrenExpression());
  }

  @Override
  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    Value expressionValue = getLastChildrenValue(node.getParent().getDescriptor());
    if (expressionValue == null) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("error.unable.to.evaluate.children.expression"));
    }

    NodeRenderer childrenRenderer = getChildrenRenderer(expressionValue.type(), (ValueDescriptor)node.getParent().getDescriptor());

    PsiExpression childrenPsiExpression = myChildrenExpression.getPsiExpression(node.getProject());
    if (childrenPsiExpression == null) {
      return null;
    }
    return DebuggerTreeNodeExpression.substituteThis(
      childrenRenderer.getChildValueExpression(node, context),
      (PsiExpression)childrenPsiExpression.copy(),
      expressionValue);
  }

  private static NodeRenderer getChildrenRenderer(Type type, ValueDescriptor parentDescriptor) {
    NodeRenderer renderer = getLastChildrenRenderer(parentDescriptor);
    if (renderer == null || type == null/* || !renderer.isApplicable(type)*/) {
      renderer = DebugProcessImpl.getDefaultRenderer(type);
      setPreferableChildrenRenderer(parentDescriptor, renderer);
    }
    return renderer;
  }

  @Override
  public CompletableFuture<Boolean> isExpandableAsync(Value value, EvaluationContext context, NodeDescriptor parentDescriptor) {
    CompletableFuture<Boolean> res = new CompletableFuture<>();
    EvaluationContextImpl evaluationContextImpl = (EvaluationContextImpl)context;
    evaluationContextImpl.getManagerThread().schedule(new PossiblySyncCommand(evaluationContextImpl.getSuspendContext()) {
      @Override
      public void syncAction(@NotNull SuspendContextImpl suspendContext) {
        EvaluationContext evaluationContext = context.createEvaluationContext(value);

        if (!StringUtil.isEmpty(myChildrenExpandable.getReferenceExpression().getText())) {
          try {
            Value expanded = myChildrenExpandable.getEvaluator(evaluationContext.getProject()).evaluate(evaluationContext);
            if (expanded instanceof BooleanValue) {
              res.complete(((BooleanValue)expanded).booleanValue());
              return;
            }
          }
          catch (EvaluateException e) {
            // ignored
          }
        }

        try {
          Value children = evaluateChildren(evaluationContext, parentDescriptor);
          DebugProcessImpl.getDefaultRenderer(value.type())
            .isExpandableAsync(children, evaluationContext, parentDescriptor)
            .whenComplete((aBoolean, throwable) -> DebuggerUtilsAsync.completeFuture(aBoolean, throwable, res));
        }
        catch (EvaluateException e) {
          res.complete(true);
        }
      }

      @Override
      protected void commandCancelled() {
        res.cancel(false);
      }
    });
    return res;
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

  @Override
  public void setClassName(String name) {
    super.setClassName(name);
    myChildrenExpression.clear();
    myChildrenExpandable.clear();
  }

  public NodeRenderer getPredictedRenderer() {
    return myPredictedRenderer;
  }

  public void setPredictedRenderer(NodeRenderer predictedRenderer) {
    myPredictedRenderer = predictedRenderer;
  }
}
