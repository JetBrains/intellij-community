// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;

/**
 * Do not extend, use {@link CompoundRendererProvider}
 */
public class CompoundReferenceRenderer extends NodeRendererImpl implements FullValueEvaluatorProvider {
  protected static final Logger LOG = Logger.getInstance(CompoundReferenceRenderer.class);
  private static final AutoToStringRenderer AUTO_TO_STRING_RENDERER = new AutoToStringRenderer();
  public static final @NonNls String UNIQUE_ID = "CompoundNodeRenderer";
  public static final @NonNls String UNIQUE_ID_OLD = "CompoundTypeRenderer";

  private ValueLabelRenderer myLabelRenderer;
  private ChildrenRenderer myChildrenRenderer;
  private ValueIconRenderer myIconRenderer = null;
  protected final NodeRendererSettings myRendererSettings;

  private FullValueEvaluatorProvider myFullValueEvaluatorProvider;

  public CompoundReferenceRenderer(NodeRendererSettings rendererSettings,
                                   String name,
                                   ValueLabelRenderer labelRenderer,
                                   ChildrenRenderer childrenRenderer) {
    super(name);
    myRendererSettings = rendererSettings;
    myLabelRenderer = labelRenderer;
    myChildrenRenderer = childrenRenderer;
    myProperties.setClassName(CommonClassNames.JAVA_LANG_OBJECT);
    LOG.assertTrue(labelRenderer == null || labelRenderer instanceof ReferenceRenderer || labelRenderer instanceof ClassRenderer);
    LOG.assertTrue(childrenRenderer == null || childrenRenderer instanceof ReferenceRenderer || childrenRenderer instanceof ClassRenderer);
  }

  public CompoundReferenceRenderer(String name, ValueLabelRenderer labelRenderer, ChildrenRenderer childrenRenderer) {
    this(NodeRendererSettings.getInstance(), name, labelRenderer, childrenRenderer);
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
  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException {
    return getLabelRenderer().calcLabel(descriptor, evaluationContext, listener);
  }

  @Override
  public @Nullable Icon calcValueIcon(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException {
    if (myIconRenderer != null) {
      return myIconRenderer.calcValueIcon(descriptor, evaluationContext, listener);
    }
    return null;
  }

  void setIconRenderer(ValueIconRenderer iconRenderer) {
    myIconRenderer = iconRenderer;
  }

  @Override
  public @Nullable XFullValueEvaluator getFullValueEvaluator(@NotNull EvaluationContextImpl evaluationContext,
                                                             @NotNull ValueDescriptorImpl valueDescriptor) {
    if (myFullValueEvaluatorProvider != null) {
      return myFullValueEvaluatorProvider.getFullValueEvaluator(evaluationContext, valueDescriptor);
    }
    return null;
  }

  @ApiStatus.Internal
  public void setFullValueEvaluator(FullValueEvaluatorProvider fullValueEvaluatorProvider) {
    myFullValueEvaluatorProvider = fullValueEvaluatorProvider;
  }

  public void setLabelRenderer(ValueLabelRenderer labelRenderer) {
    final ValueLabelRenderer prevRenderer = getLabelRenderer();
    myLabelRenderer = isBaseRenderer(labelRenderer) ? null : labelRenderer;
    final ValueLabelRenderer currentRenderer = getLabelRenderer();
    if (prevRenderer != currentRenderer) {
      if (currentRenderer instanceof ReferenceRenderer) {
        ((ReferenceRenderer)currentRenderer).setClassName(getClassName());
      }
    }
  }

  public void setChildrenRenderer(ChildrenRenderer childrenRenderer) {
    final ChildrenRenderer prevRenderer = getChildrenRenderer();
    myChildrenRenderer = isBaseRenderer(childrenRenderer) ? null : childrenRenderer;
    final ChildrenRenderer currentRenderer = getChildrenRenderer();
    if (prevRenderer != currentRenderer) {
      if (currentRenderer instanceof ReferenceRenderer) {
        ((ReferenceRenderer)currentRenderer).setClassName(getClassName());
      }
    }
  }

  public ChildrenRenderer getChildrenRenderer() {
    return myChildrenRenderer != null ? myChildrenRenderer : getDefaultRenderer();
  }

  private NodeRenderer getDefaultRenderer() {
    String name = getClassName();
    if (TypeConversionUtil.isPrimitive(name)) {
      return myRendererSettings.getPrimitiveRenderer();
    }
    return name.endsWith("]") ? myRendererSettings.getArrayRenderer() : AUTO_TO_STRING_RENDERER;
  }

  public ValueLabelRenderer getLabelRenderer() {
    return myLabelRenderer != null ? myLabelRenderer : getDefaultRenderer();
  }

  private ChildrenRenderer getRawChildrenRenderer() {
    return myChildrenRenderer == getDefaultRenderer() ? null : myChildrenRenderer;
  }

  private ValueLabelRenderer getRawLabelRenderer() {
    return myLabelRenderer == getDefaultRenderer() ? null : myLabelRenderer;
  }

  public void setClassName(@NotNull String name) {
    myProperties.setClassName(name);
    if (getRawLabelRenderer() != null) {
      if (myLabelRenderer instanceof ReferenceRenderer) {
        ((ReferenceRenderer)myLabelRenderer).setClassName(name);
      }
    }

    if (getRawChildrenRenderer() != null) {
      if (myChildrenRenderer instanceof ReferenceRenderer) {
        ((ReferenceRenderer)myChildrenRenderer).setClassName(name);
      }
    }
  }

  @Override
  public boolean isApplicable(Type type) {
    String className = getClassName();
    if (!StringUtil.isEmpty(className)) {
      return DebuggerUtils.instanceOf(type, className);
    }
    return getLabelRenderer().isApplicable(type) && getChildrenRenderer().isApplicable(type);
  }

  @NotNull
  public String getClassName() {
    return myProperties.getClassName();
  }

  protected final PsiElement getContext(Project project, DebuggerContext context) {
    DebugProcess process = context.getDebugProcess();
    GlobalSearchScope scope = process != null ? process.getSearchScope() : GlobalSearchScope.allScope(project);
    return DebuggerUtils.findClass(getClassName(), project, scope);
  }

  protected final PsiElement getChildValueExpression(String text, DebuggerTreeNode node, DebuggerContext context) {
    Project project = node.getProject();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    return elementFactory.createExpressionFromText(text, getContext(project, context));
  }

  public boolean isBaseRenderer(Renderer renderer) {
    return renderer == AUTO_TO_STRING_RENDERER ||
           renderer == myRendererSettings.getClassRenderer() ||
           renderer == myRendererSettings.getPrimitiveRenderer() ||
           renderer == myRendererSettings.getArrayRenderer();
  }

  private static final class AutoToStringRenderer extends ToStringRenderer {
    private AutoToStringRenderer() {
      setIsApplicableChecker(type -> CompletableFuture.completedFuture(type instanceof ReferenceType));
    }

    @Override
    public String getUniqueId() {
      return "AutoToString";
    }

    @Override
    public boolean isOnDemand(EvaluationContext evaluationContext, ValueDescriptor valueDescriptor) {
      return NodeRendererSettings.getInstance().getToStringRenderer().isOnDemand(evaluationContext, valueDescriptor);
    }

    @Override
    public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) {
      NodeRendererSettings nodeRendererSettings = NodeRendererSettings.getInstance();
      ToStringRenderer toStringRenderer = nodeRendererSettings.getToStringRenderer();
      CompletableFuture<Boolean> toStringApplicable = CompletableFuture.completedFuture(false);
      if (toStringRenderer.isEnabled()) {
        toStringApplicable = toStringRenderer.isApplicableAsync(descriptor.getType());
      }
      CompletableFuture<NodeRenderer> renderer = toStringApplicable
        .thenApply(applicable -> applicable ? toStringRenderer : nodeRendererSettings.getClassRenderer());
      return calcLabel(renderer, descriptor, evaluationContext, listener);
    }
  }

  @Override
  public String getUniqueId() {
    return UNIQUE_ID;
  }

  @Override
  public CompoundReferenceRenderer clone() {
    CompoundReferenceRenderer renderer = (CompoundReferenceRenderer)super.clone();
    renderer.myLabelRenderer = (myLabelRenderer != null) ? (ValueLabelRenderer)myLabelRenderer.clone() : null;
    renderer.myChildrenRenderer = (myChildrenRenderer != null) ? (ChildrenRenderer)myChildrenRenderer.clone() : null;
    return renderer;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    for (Element elem : element.getChildren(NodeRendererSettings.RENDERER_TAG)) {
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

  @Override
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
