// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.JavaDebuggerBundle;
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
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public abstract class NodeRendererImpl implements NodeRenderer {
  public static final String DEFAULT_NAME = "unnamed";

  protected BasicRendererProperties myProperties;
  private final String myDefaultName;
  private Function<? super Type, ? extends CompletableFuture<Boolean>> myIsApplicableChecker = null;

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
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
  }

  @Override
  public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    return null;
  }

  @ApiStatus.Internal
  public void setIsApplicableChecker(@NotNull Function<? super Type, ? extends CompletableFuture<Boolean>> isApplicableAsync) {
    myIsApplicableChecker = isApplicableAsync;
  }

  @Override
  public final CompletableFuture<Boolean> isApplicableAsync(Type type) {
    if (myIsApplicableChecker != null) {
      return myIsApplicableChecker.apply(type);
    }
    return NodeRenderer.super.isApplicableAsync(type);
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
  public String calcIdLabel(ValueDescriptor descriptor, DebugProcess process, DescriptorLabelListener labelListener) {
    Value value = descriptor.getValue();
    if (!(value instanceof ObjectReference) || !isShowType()) {
      return null;
    }
    return ValueDescriptorImpl.calcIdLabel(descriptor, labelListener);
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
      renderer.append(JavaDebuggerBundle.message("renderer.name", myRenderer.getName()));
    }

    @Override
    public int hashCode() {
      return myRenderer.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Overhead && myRenderer.equals(((Overhead)obj).myRenderer);
    }
  }

  public static String calcLabel(CompletableFuture<NodeRenderer> renderer,
                                 ValueDescriptor descriptor,
                                 EvaluationContext evaluationContext,
                                 DescriptorLabelListener listener) {
    return renderer.thenApply(r -> {
      try {
        return r.calcLabel(descriptor, evaluationContext, listener);
      }
      catch (EvaluateException e) {
        descriptor.setValueLabelFailed(e);
        listener.labelChanged();
        return "";
      }
    }).getNow(XDebuggerUIConstants.getCollectingDataMessage());
  }
}
