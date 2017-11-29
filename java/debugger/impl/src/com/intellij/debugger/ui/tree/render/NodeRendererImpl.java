/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.overhead.OverheadProducer;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleColoredComponent;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 9, 2005
 */
public abstract class NodeRendererImpl implements NodeRenderer{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.NodeRendererImpl");
  protected BasicRendererProperties myProperties;

  protected NodeRendererImpl() {
    this("unnamed");
  }

  protected NodeRendererImpl(@NotNull String presentableName) {
    this(presentableName, false);
  }

  protected NodeRendererImpl(@NotNull String presentableName, boolean enabledDefaultValue) {
    myProperties = new BasicRendererProperties(enabledDefaultValue);
    myProperties.setName(presentableName);
    myProperties.setEnabled(enabledDefaultValue);
  }

  @Override
  public String getName() {
    return myProperties.getName();
  }

  @Override
  public void setName(String name) {
    myProperties.setName(name);
  }

  @Override
  public boolean isEnabled() {
    return myProperties.isEnabled();
  }

  @Override
  public void setEnabled(boolean enabled) {
    myProperties.setEnabled(enabled);
  }

  public boolean isShowType() {
    return myProperties.isShowType();
  }

  public void setShowType(boolean showType) {
    myProperties.setShowType(showType);
  }

  @Override
  public Icon calcValueIcon(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException {
    return null;
  }

  @Override
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
  }

  @Override
  public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    return null;
  }

  @Override
  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return false;
  }

  @Override
  public NodeRendererImpl clone() {
    try {
      final NodeRendererImpl cloned = (NodeRendererImpl)super.clone();
      cloned.myProperties = myProperties.clone();
      return cloned;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return null;
  }

  @Override
  public void readExternal(Element element) {
    myProperties.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) {
    myProperties.writeExternal(element);
  }

  public String toString() {
    return getName();
  }

  @Nullable
  public String getIdLabel(Value value, DebugProcess process) {
    return value instanceof ObjectReference && isShowType() ? ValueDescriptorImpl.getIdLabel((ObjectReference)value) : null;
  }

  public boolean hasOverhead() {
    return false;
  }

  public static class Overhead implements OverheadProducer {
    private final NodeRendererImpl myRenderer;

    public Overhead(@NotNull NodeRendererImpl renderer) {
      myRenderer = renderer;
    }

    @Override
    public boolean isEnabled() {
      return myRenderer.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
      myRenderer.setEnabled(enabled);
      NodeRendererSettings.getInstance().fireRenderersChanged();
    }

    @Override
    public void customizeRenderer(SimpleColoredComponent renderer) {
      renderer.append(myRenderer.getName() + " renderer");
    }

    @Override
    public int hashCode() {
      return myRenderer.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof CompoundTypeRenderer.Overhead && myRenderer.equals(((CompoundTypeRenderer.Overhead)obj).myRenderer);
    }
  }
}
