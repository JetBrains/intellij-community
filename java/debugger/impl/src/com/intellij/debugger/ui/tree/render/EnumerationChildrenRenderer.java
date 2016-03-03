/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.descriptors.data.UserExpressionData;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * User: lex
 * Date: Dec 19, 2003
 * Time: 1:25:15 PM
 */
public final class EnumerationChildrenRenderer extends ReferenceRenderer implements ChildrenRenderer{
  public static final @NonNls String UNIQUE_ID = "EnumerationChildrenRenderer";

  private List<Pair<String, TextWithImports>> myChildren;
  public static final @NonNls String CHILDREN_EXPRESSION = "ChildrenExpression";
  public static final @NonNls String CHILD_NAME = "Name";

  public EnumerationChildrenRenderer() {
    this(new ArrayList<>());
  }

  public EnumerationChildrenRenderer(List<Pair<String, TextWithImports>> children) {
    super();
    myChildren = children;
  }

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public EnumerationChildrenRenderer clone() {
    return (EnumerationChildrenRenderer)super.clone();
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    myChildren.clear();

    List<Element> children = element.getChildren(CHILDREN_EXPRESSION);
    for (Element item : children) {
      String name = item.getAttributeValue(CHILD_NAME);
      TextWithImports text = DebuggerUtils.getInstance().readTextWithImports(item.getChildren().get(0));

      myChildren.add(Pair.create(name, text));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);

    for (Pair<String, TextWithImports> pair : myChildren) {
      Element child = new Element(CHILDREN_EXPRESSION);
      child.setAttribute(CHILD_NAME, pair.getFirst());
      child.addContent(DebuggerUtils.getInstance().writeTextWithImports(pair.getSecond()));

      element.addContent(child);
    }
  }

  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    NodeManager nodeManager = builder.getNodeManager();
    NodeDescriptorFactory descriptorFactory = builder.getDescriptorManager();

    List<DebuggerTreeNode> children = new ArrayList<>();
    for (Pair<String, TextWithImports> pair : myChildren) {
      children.add(nodeManager.createNode(descriptorFactory.getUserExpressionDescriptor(
        builder.getParentDescriptor(),
        new UserExpressionData((ValueDescriptorImpl)builder.getParentDescriptor(), getClassName(), pair.getFirst(), pair.getSecond())), evaluationContext)
      );
    }
    builder.setChildren(children);
  }

  public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    return ((ValueDescriptor) node.getDescriptor()).getDescriptorEvaluation(context);
  }

  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return myChildren.size() > 0;
  }

  public List<Pair<String, TextWithImports>> getChildren() {
    return myChildren;
  }

  public void setChildren(List<Pair<String, TextWithImports>> children) {
    myChildren = children;
  }
}
