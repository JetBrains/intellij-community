package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jdom.Element;

import java.util.List;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class CompoundNodeRenderer extends NodeRendererImpl{
  public static final String UNIQUE_ID = "CompoundNodeRenderer";

  protected ValueLabelRenderer myLabelRenderer;
  protected ChildrenRenderer myChildrenRenderer;

  public CompoundNodeRenderer(RendererProvider provider, String name, ValueLabelRenderer labelRenderer, ChildrenRenderer childrenRenderer) {
    super(provider, UNIQUE_ID);
    setName(name);
    myLabelRenderer = labelRenderer;
    myChildrenRenderer = childrenRenderer;
  }

  public Renderer clone() {
    CompoundNodeRenderer renderer = (CompoundNodeRenderer)super.clone();
    renderer.myLabelRenderer    = myLabelRenderer    != null ? (ValueLabelRenderer)myLabelRenderer.clone() : null;
    renderer.myChildrenRenderer = myChildrenRenderer != null ? (ChildrenRenderer)myChildrenRenderer.clone() : null;
    return renderer;
  }

  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    getChildrenRenderer().buildChildren(value, builder, evaluationContext);
  }

  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    return getChildrenRenderer().getChildValueExpression(node, context);
  }

  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return getChildrenRenderer().isExpandable(value, evaluationContext, parentDescriptor);
  }

  public boolean isApplicable(Type type) {
    return getLabelRenderer().isApplicable(type) && getChildrenRenderer().isApplicable(type);
  }

  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException {
    return getLabelRenderer().calcLabel(descriptor, evaluationContext, listener);
  }

  public ValueLabelRenderer getLabelRenderer() {
    return myLabelRenderer;
  }

  public ChildrenRenderer getChildrenRenderer() {
    return myChildrenRenderer;
  }

  public void setLabelRenderer(ValueLabelRenderer labelRenderer) {
    myLabelRenderer = labelRenderer;
  }

  public void setChildrenRenderer(ChildrenRenderer childrenRenderer) {
    myChildrenRenderer = childrenRenderer;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    if(getName() == null) {
      setName("<unknown>");
    }
    List<Element> children = element.getChildren(NodeRendererExternalizer.RENDERER_TAG);
    myLabelRenderer = (ValueLabelRenderer) NodeRendererExternalizer.readRenderer(children.get(0));
    myChildrenRenderer = (ChildrenRenderer)   NodeRendererExternalizer.readRenderer(children.get(1));
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    final Element labelRendererElement = NodeRendererExternalizer.writeRenderer(myLabelRenderer);
    element.addContent(labelRendererElement);
    final Element childrenRendererElement = NodeRendererExternalizer.writeRenderer(myChildrenRenderer);
    element.addContent(childrenRendererElement);
  }
}
