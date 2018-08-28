// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleColoredComponent;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class NodeRendererImpl implements NodeRenderer {
  public static final String DEFAULT_NAME = "unnamed";

  protected BasicRendererProperties myProperties;
  private String myDefaultName;

  protected NodeRendererImpl() {
    this(DEFAULT_NAME, false);
  }

  protected NodeRendererImpl(@NotNull String presentableName) {
    this(presentableName, false);
  }

  protected NodeRendererImpl(@NotNull String presentableName, boolean enabledDefaultValue) {
    myDefaultName = presentableName;
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
      throw new RuntimeException(e);
    }
  }

  @Override
  public void readExternal(Element element) {
    myProperties.readExternal(element, myDefaultName);
  }

  @Override
  public void writeExternal(Element element) {
    myProperties.writeExternal(element, myDefaultName);
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
