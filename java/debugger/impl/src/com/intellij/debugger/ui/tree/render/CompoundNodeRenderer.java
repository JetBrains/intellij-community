// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CompoundNodeRenderer extends NodeRendererImpl{
  public static final @NonNls String UNIQUE_ID = "CompoundNodeRenderer";

  private ValueLabelRenderer myLabelRenderer;
  private ChildrenRenderer myChildrenRenderer;
  protected final NodeRendererSettings myRendererSettings;

  public CompoundNodeRenderer(NodeRendererSettings rendererSettings, String name, ValueLabelRenderer labelRenderer, ChildrenRenderer childrenRenderer) {
    super(name);
    myRendererSettings = rendererSettings;
    myLabelRenderer = labelRenderer;
    myChildrenRenderer = childrenRenderer;
  }

  @Override
  public String getUniqueId() {
    return UNIQUE_ID;
  }

  @Override
  public CompoundNodeRenderer clone() {
    CompoundNodeRenderer renderer = (CompoundNodeRenderer)super.clone();
    renderer.myLabelRenderer    = (myLabelRenderer    != null) ? (ValueLabelRenderer)myLabelRenderer.clone() : null;
    renderer.myChildrenRenderer = (myChildrenRenderer != null) ? (ChildrenRenderer)myChildrenRenderer.clone() : null;
    return renderer;
  }

  @Override
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    getChildrenRenderer().buildChildren(value, builder, evaluationContext);
  }

  @Override
  public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    return getChildrenRenderer().getChildValueExpression(node, context);
  }

  @Override
  public CompletableFuture<Boolean> isExpandableAsync(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return getChildrenRenderer().isExpandableAsync(value, evaluationContext, parentDescriptor);
  }

  @Override
  public boolean isApplicable(Type type) {
    return getLabelRenderer().isApplicable(type) && getChildrenRenderer().isApplicable(type);
  }

  @Override
  public CompletableFuture<Boolean> isApplicableAsync(Type type) {
    return getLabelRenderer().isApplicableAsync(type)
      .thenCombine(getChildrenRenderer().isApplicableAsync(type), (res1, res2) -> res1 && res2);
  }

  @Override
  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException {
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

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    List<Element> children = element.getChildren(NodeRendererSettings.RENDERER_TAG);
    if (children != null) {
      for (Element elem : children) {
        String role = elem.getAttributeValue("role");
        if (role == null) {
          continue;
        }
        if ("label".equals(role)) {
          myLabelRenderer = (ValueLabelRenderer)myRendererSettings.readRenderer(elem);
        }
        else if ("children".equals(role)) {
          myChildrenRenderer = (ChildrenRenderer)myRendererSettings.readRenderer(elem);
        }
      }
    }
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    if (myLabelRenderer != null) {
      final Element labelRendererElement = myRendererSettings.writeRenderer(myLabelRenderer);
      labelRendererElement.setAttribute("role", "label");
      element.addContent(labelRendererElement);
    }
    if (myChildrenRenderer != null) {
      final Element childrenRendererElement = myRendererSettings.writeRenderer(myChildrenRenderer);
      childrenRendererElement.setAttribute("role", "children");
      element.addContent(childrenRendererElement);
    }
  }
}
