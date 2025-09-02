// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.descriptors.data.UserExpressionData;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationOrigin;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class EnumerationChildrenRenderer extends ReferenceRenderer implements ChildrenRenderer {
  public static final @NonNls String UNIQUE_ID = "EnumerationChildrenRenderer";

  private boolean myAppendDefaultChildren;
  private List<ChildInfo> myChildren;

  public static final @NonNls String APPEND_DEFAULT_NAME = "AppendDefault";
  public static final @NonNls String CHILDREN_EXPRESSION = "ChildrenExpression";
  public static final @NonNls String CHILD_NAME = "Name";
  public static final @NonNls String CHILD_ONDEMAND = "OnDemand";

  public EnumerationChildrenRenderer() {
    this(new ArrayList<>());
  }

  public EnumerationChildrenRenderer(List<ChildInfo> children) {
    super();
    myChildren = children;
  }

  public void setAppendDefaultChildren(boolean appendDefaultChildren) {
    myAppendDefaultChildren = appendDefaultChildren;
  }

  public boolean isAppendDefaultChildren() {
    return myAppendDefaultChildren;
  }

  @Override
  public String getUniqueId() {
    return UNIQUE_ID;
  }

  @Override
  public EnumerationChildrenRenderer clone() {
    return (EnumerationChildrenRenderer)super.clone();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    myChildren.clear();

    myAppendDefaultChildren = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, APPEND_DEFAULT_NAME));

    List<Element> children = element.getChildren(CHILDREN_EXPRESSION);
    for (Element item : children) {
      String name = item.getAttributeValue(CHILD_NAME);
      TextWithImports text = DebuggerUtils.getInstance().readTextWithImports(item.getChildren().get(0));
      boolean onDemand = Boolean.parseBoolean(item.getAttributeValue(CHILD_ONDEMAND));

      myChildren.add(new ChildInfo(name, text, onDemand));
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);

    if (myAppendDefaultChildren) {
      JDOMExternalizerUtil.writeField(element, APPEND_DEFAULT_NAME, "true");
    }

    for (ChildInfo childInfo : myChildren) {
      Element child = new Element(CHILDREN_EXPRESSION);
      child.setAttribute(CHILD_NAME, childInfo.myName);
      if (childInfo.myOnDemand) {
        child.setAttribute(CHILD_ONDEMAND, "true");
      }
      child.addContent(DebuggerUtils.getInstance().writeTextWithImports(childInfo.myExpression));

      element.addContent(child);
    }
  }

  @Override
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    NodeManager nodeManager = builder.getNodeManager();
    NodeDescriptorFactory descriptorFactory = builder.getDescriptorManager();

    List<DebuggerTreeNode> children = new ArrayList<>();
    int idx = 0;
    for (ChildInfo childInfo : myChildren) {
      UserExpressionData data = new UserExpressionData((ValueDescriptorImpl)builder.getParentDescriptor(),
                                                       getClassName(),
                                                       childInfo.myName,
                                                       childInfo.myExpression);
      data.setEnumerationIndex(idx++);
      UserExpressionDescriptor descriptor = descriptorFactory.getUserExpressionDescriptor(builder.getParentDescriptor(), data);
      XEvaluationOrigin.setOrigin(descriptor, XEvaluationOrigin.RENDERER);
      if (childInfo.myOnDemand) {
        descriptor.putUserData(OnDemandRenderer.ON_DEMAND_CALCULATED, false);
      }
      children.add(nodeManager.createNode(descriptor, evaluationContext));
    }
    builder.addChildren(children, !myAppendDefaultChildren);

    if (myAppendDefaultChildren) {
      DebugProcessImpl.getDefaultRenderer(value).buildChildren(value, builder, evaluationContext);
    }
  }

  @Override
  public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    return ((ValueDescriptor)node.getDescriptor()).getDescriptorEvaluation(context);
  }

  @Override
  public CompletableFuture<Boolean> isExpandableAsync(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    if (!myChildren.isEmpty()) {
      return CompletableFuture.completedFuture(true);
    }
    if (myAppendDefaultChildren) {
      return DebugProcessImpl.getDefaultRenderer(value).isExpandableAsync(value, evaluationContext, parentDescriptor);
    }
    return CompletableFuture.completedFuture(false);
  }

  public List<ChildInfo> getChildren() {
    return myChildren;
  }

  public void setChildren(List<ChildInfo> children) {
    myChildren = children;
  }

  public static @Nullable EnumerationChildrenRenderer getCurrent(ValueDescriptorImpl valueDescriptor) {
    Renderer renderer = valueDescriptor.getLastRenderer();
    if (renderer instanceof CompoundReferenceRenderer &&
        NodeRendererSettings.getInstance().getCustomRenderers().contains((NodeRenderer)renderer)) {
      ChildrenRenderer childrenRenderer = ((CompoundReferenceRenderer)renderer).getChildrenRenderer();
      if (childrenRenderer instanceof EnumerationChildrenRenderer) {
        return (EnumerationChildrenRenderer)childrenRenderer;
      }
    }
    return null;
  }

  public static class ChildInfo implements Cloneable {
    public String myName;
    public TextWithImports myExpression;
    public boolean myOnDemand;

    public ChildInfo(String name, TextWithImports expression, boolean onDemand) {
      myName = name;
      myExpression = expression;
      myOnDemand = onDemand;
    }
  }
}
