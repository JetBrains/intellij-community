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
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CompoundTypeRenderer extends CompoundNodeRenderer {
  public static final @NonNls String UNIQUE_ID = "CompoundTypeRenderer";
  protected static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer");
  private static final AutoToStringRenderer AUTO_TO_STRING_RENDERER = new AutoToStringRenderer();

  public CompoundTypeRenderer(NodeRendererSettings rendererSettings,
                              String name,
                              ValueLabelRenderer labelRenderer,
                              ChildrenRenderer childrenRenderer) {
    super(rendererSettings, name, labelRenderer, childrenRenderer);
    myProperties.setClassName(CommonClassNames.JAVA_LANG_OBJECT);
    LOG.assertTrue(labelRenderer == null || labelRenderer instanceof TypeRenderer);
    LOG.assertTrue(childrenRenderer == null || childrenRenderer instanceof TypeRenderer);
  }

  public void setLabelRenderer(ValueLabelRenderer labelRenderer) {
    final ValueLabelRenderer prevRenderer = getLabelRenderer();
    super.setLabelRenderer(isBaseRenderer(labelRenderer) ? null : labelRenderer);
    final ValueLabelRenderer currentRenderer = getLabelRenderer();
    if (prevRenderer != currentRenderer) {
      if (currentRenderer instanceof TypeRenderer) {
        ((TypeRenderer)currentRenderer).setClassName(getClassName());
      }
    }
  }

  public void setChildrenRenderer(ChildrenRenderer childrenRenderer) {
    final ChildrenRenderer prevRenderer = getChildrenRenderer();
    super.setChildrenRenderer(isBaseRenderer(childrenRenderer) ? null : childrenRenderer);
    final ChildrenRenderer currentRenderer = getChildrenRenderer();
    if (prevRenderer != currentRenderer) {
      if (currentRenderer instanceof TypeRenderer) {
        ((TypeRenderer)currentRenderer).setClassName(getClassName());
      }
    }
  }

  public ChildrenRenderer getChildrenRenderer() {
    final ChildrenRenderer childrenRenderer = super.getChildrenRenderer();
    return childrenRenderer != null ? childrenRenderer : getDefaultRenderer();
  }

  private NodeRenderer getDefaultRenderer() {
    String name = getClassName();
    if (TypeConversionUtil.isPrimitive(name)) {
      return myRendererSettings.getPrimitiveRenderer();
    }
    return name.endsWith("]") ? myRendererSettings.getArrayRenderer() : AUTO_TO_STRING_RENDERER;
  }

  public ValueLabelRenderer getLabelRenderer() {
    final ValueLabelRenderer labelRenderer = super.getLabelRenderer();
    return labelRenderer != null ? labelRenderer : getDefaultRenderer();
  }

  private ChildrenRenderer getRawChildrenRenderer() {
    NodeRenderer classRenderer = getDefaultRenderer();
    final ChildrenRenderer originalRenderer = super.getChildrenRenderer();
    return originalRenderer == classRenderer ? null : originalRenderer;
  }

  private ValueLabelRenderer getRawLabelRenderer() {
    NodeRenderer classRenderer = getDefaultRenderer();
    final ValueLabelRenderer originalRenderer = super.getLabelRenderer();
    return originalRenderer == classRenderer ? null : originalRenderer;
  }

  public boolean isApplicable(Type type) {
    if (DebuggerUtils.instanceOf(type, getClassName())) {
      return super.isApplicable(type);
    }
    return false;
  }

  @Override
  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public void setClassName(@NotNull String name) {
    myProperties.setClassName(name);
    if (getRawLabelRenderer() != null) {
      final ValueLabelRenderer originalLabelRenderer = super.getLabelRenderer();
      if (originalLabelRenderer instanceof TypeRenderer) {
        ((TypeRenderer)originalLabelRenderer).setClassName(name);
      }
    }

    if (getRawChildrenRenderer() != null) {
      final ChildrenRenderer originalChildrenRenderer = super.getChildrenRenderer();
      if (originalChildrenRenderer instanceof TypeRenderer) {
        ((TypeRenderer)originalChildrenRenderer).setClassName(name);
      }
    }
  }

  public
  @NotNull
  String getClassName() {
    return myProperties.getClassName();
  }

  protected final PsiElement getContext(Project project, DebuggerContext context) {
    DebugProcess process = context.getDebugProcess();
    GlobalSearchScope scope = process != null ? process.getSearchScope() : GlobalSearchScope.allScope(project);
    return DebuggerUtils.findClass(getClassName(), project, scope);
  }

  protected final PsiElement getChildValueExpression(String text, DebuggerTreeNode node, DebuggerContext context) {
    Project project = node.getProject();
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    return elementFactory.createExpressionFromText(text, getContext(project, context));
  }

  public boolean isBaseRenderer(Renderer renderer) {
    return renderer == AUTO_TO_STRING_RENDERER ||
           renderer == myRendererSettings.getClassRenderer() ||
           renderer == myRendererSettings.getPrimitiveRenderer() ||
           renderer == myRendererSettings.getArrayRenderer();
  }

  @Override
  public boolean hasOverhead() {
    return true;
  }

  private static class AutoToStringRenderer extends ToStringRenderer {
    @Override
    public String getUniqueId() {
      return "AutoToString";
    }

    @Override
    public boolean isApplicable(Type type) {
      return type instanceof ReferenceType;
    }

    @Override
    public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
      throws EvaluateException {
      NodeRendererSettings nodeRendererSettings = NodeRendererSettings.getInstance();
      ToStringRenderer toStringRenderer = nodeRendererSettings.getToStringRenderer();
      if (toStringRenderer.isEnabled() && toStringRenderer.isApplicable(descriptor.getType())) {
        return toStringRenderer.calcLabel(descriptor, evaluationContext, listener);
      }
      else {
        return nodeRendererSettings.getClassRenderer().calcLabel(descriptor, evaluationContext, listener);
      }
    }
  }
}
