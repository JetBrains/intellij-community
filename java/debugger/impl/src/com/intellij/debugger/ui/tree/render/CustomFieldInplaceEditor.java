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

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.UserExpressionDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRestorer;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;

/**
 * @author egor
 */
public class CustomFieldInplaceEditor extends XDebuggerTreeInplaceEditor {
  private final UserExpressionDescriptorImpl myDescriptor;
  protected final EnumerationChildrenRenderer myRenderer;

  public CustomFieldInplaceEditor(@NotNull XDebuggerTreeNode node,
                                  @Nullable UserExpressionDescriptorImpl descriptor,
                                  @Nullable EnumerationChildrenRenderer renderer) {
    super(node, "customField");
    myDescriptor = descriptor;
    myRenderer = renderer;
    myExpressionEditor.setExpression(descriptor != null ? TextWithImportsImpl.toXExpression(descriptor.getEvaluationText()) : null);

    ValueDescriptorImpl parentDescriptor = ((JavaValue)((XValueContainerNode)node.getParent()).getValueContainer()).getDescriptor();
    Pair<PsiElement, PsiType> pair = DebuggerUtilsImpl.getPsiClassAndType(getTypeName(parentDescriptor), getProject());
    if (pair.first != null) {
      myExpressionEditor.setContext(pair.first);
    }
  }

  public static void editNew(@NotNull XValueNodeImpl parentNode) {
    ValueDescriptorImpl descriptor = ((JavaValue)parentNode.getValueContainer()).getDescriptor();
    EnumerationChildrenRenderer renderer = EnumerationChildrenRenderer.getCurrent(descriptor);
    XDebuggerTreeNode newNode = parentNode.addTemporaryEditorNode(AllIcons.Debugger.Watch, null);
    DebuggerUIUtil.invokeLater(() -> new CustomFieldInplaceEditor(newNode, null, renderer) {
      @Override
      public void cancelEditing() {
        super.cancelEditing();
        parentNode.removeTemporaryEditorNode(newNode);
      }

      @Override
      protected List<EnumerationChildrenRenderer.ChildInfo> getRendererChildren() {
        if (myRenderer != null) {
          return myRenderer.getChildren();
        }
        String name = getTypeName(descriptor);
        EnumerationChildrenRenderer enumerationChildrenRenderer = new EnumerationChildrenRenderer();
        enumerationChildrenRenderer.setAppendDefaultChildren(true);

        Renderer lastRenderer = descriptor.getLastRenderer();
        if (lastRenderer instanceof CompoundNodeRenderer &&
            NodeRendererSettings.getInstance().getCustomRenderers().contains((NodeRenderer)lastRenderer) &&
            !(((CompoundNodeRenderer)lastRenderer).getChildrenRenderer() instanceof ExpressionChildrenRenderer)) {
            ((CompoundNodeRenderer)lastRenderer).setChildrenRenderer(enumerationChildrenRenderer);
        }
        else {
          NodeRenderer renderer =
            NodeRendererSettings.getInstance().createCompoundTypeRenderer(name, name, null, enumerationChildrenRenderer);
          renderer.setEnabled(true);
          NodeRendererSettings.getInstance().getCustomRenderers().addRenderer(renderer);
          NodeRendererSettings.getInstance().fireRenderersChanged();
        }
        return enumerationChildrenRenderer.getChildren();
      }
    }.show());
  }

  @Nullable
  private static String getTypeName(ValueDescriptorImpl descriptor) {
    Type type = descriptor.getType();
    return type != null ? type.name() : null;
  }

  protected List<EnumerationChildrenRenderer.ChildInfo> getRendererChildren() {
    return myRenderer.getChildren();
  }

  @Override
  public void doOKAction() {
    List<EnumerationChildrenRenderer.ChildInfo> children = getRendererChildren();
    TextWithImports newText = TextWithImportsImpl.fromXExpression(myExpressionEditor.getExpression());
    if (myDescriptor == null) {
      children.add(0, new EnumerationChildrenRenderer.ChildInfo("", newText, false));
    }
    else {
      int index = myDescriptor.getEnumerationIndex();
      EnumerationChildrenRenderer.ChildInfo old = children.get(index);
      children.set(index, new EnumerationChildrenRenderer.ChildInfo(old.myName, newText, old.myOnDemand));
    }

    myTree.putClientProperty(XDebuggerTreeRestorer.SELECTION_PATH_PROPERTY,
                             createDummySelectionTreePath(newText.getText(), (XDebuggerTreeNode)myNode.getParent()));

    XDebuggerUtilImpl.rebuildTreeAndViews(myTree);

    super.doOKAction();
  }

  private static TreePath createDummySelectionTreePath(String name, XDebuggerTreeNode parentNode) {
    return new XValueNodeImpl(parentNode.getTree(), parentNode, name, new XValue() {
      @Override
      public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
      }
    }).getPath();
  }

  @Nullable
  @Override
  protected Rectangle getEditorBounds() {
    Rectangle bounds = super.getEditorBounds();
    if (bounds == null) {
      return null;
    }
    int afterIconX = getAfterIconX();
    bounds.x += afterIconX;
    bounds.width -= afterIconX;
    return bounds;
  }
}
