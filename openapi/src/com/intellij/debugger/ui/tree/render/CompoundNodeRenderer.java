package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.DebuggerContext;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jdom.Element;

import java.util.List;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class CompoundNodeRenderer implements NodeRenderer{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.CompoundNodeRenderer");

  public static final String UNIQUE_ID = "CompoundNodeRenderer";

  private String myName;
  private RendererProvider     myRendererProvider;
  
  protected ValueLabelRenderer   myLabelRenderer;
  protected ChildrenRenderer     myChildrenRenderer;

  public CompoundNodeRenderer(RendererProvider provider, String name, ValueLabelRenderer labelRenderer, ChildrenRenderer childrenRenderer) {
    myRendererProvider = provider;
    myName = name;
    myLabelRenderer = labelRenderer;
    myChildrenRenderer = childrenRenderer;
  }

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public RendererProvider getRendererProvider() {
    return myRendererProvider;
  }

  public String getName() {
    return myName;
  }

  public void setName(String text) {
    myName = text;
  }

  public NodeRenderer clone() {
    try {
      CompoundNodeRenderer renderer = (CompoundNodeRenderer)super.clone();
      renderer.myLabelRenderer    = myLabelRenderer    != null ? myLabelRenderer.clone() : null;
      renderer.myChildrenRenderer = myChildrenRenderer != null ? myChildrenRenderer.clone() : null;
      return renderer;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }

  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    getChildrenRenderer().buildChildren(value, builder, evaluationContext);
  }

  public PsiExpression getChildrenValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    return getChildrenRenderer().getChildrenValueExpression(node, context);
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
    myName = element.getAttributeValue("NAME");
    if(myName == null) myName = "<unknown>";
    List<Element> children = element.getChildren();
    myLabelRenderer    = (ValueLabelRenderer) NodeRendererExternalizer.readRenderer(children.get(0));
    myChildrenRenderer = (ChildrenRenderer)   NodeRendererExternalizer.readRenderer(children.get(1));
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute("NAME", myName);
    element.addContent(NodeRendererExternalizer.writeRenderer(myLabelRenderer));
    element.addContent(NodeRendererExternalizer.writeRenderer(myChildrenRenderer));
  }
}
