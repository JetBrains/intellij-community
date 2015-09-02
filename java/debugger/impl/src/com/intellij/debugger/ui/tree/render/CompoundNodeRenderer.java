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

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public CompoundNodeRenderer clone() {
    CompoundNodeRenderer renderer = (CompoundNodeRenderer)super.clone();
    renderer.myLabelRenderer    = (myLabelRenderer    != null) ? (ValueLabelRenderer)myLabelRenderer.clone() : null;
    renderer.myChildrenRenderer = (myChildrenRenderer != null) ? (ChildrenRenderer)myChildrenRenderer.clone() : null;
    return renderer;
  }

  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    getChildrenRenderer().buildChildren(value, builder, evaluationContext);
  }

  public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
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
