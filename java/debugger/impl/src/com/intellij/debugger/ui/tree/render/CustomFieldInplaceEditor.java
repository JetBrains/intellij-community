// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.UserExpressionDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.util.concurrency.AppExecutorUtil;
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

    ValueDescriptorImpl parentDescriptor = ((JavaValue)((XValueContainerNode<?>)node.getParent()).getValueContainer()).getDescriptor();
    ReadAction.nonBlocking(() -> DebuggerUtilsImpl.getPsiClassAndType(getTypeName(parentDescriptor), getProject()).first)
      .finishOnUiThread(ModalityState.defaultModalityState(), context -> {
        if (context != null) {
          myExpressionEditor.setContext(context);
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  public static void editNew(@NotNull XValueNodeImpl parentNode) {
    ValueDescriptorImpl descriptor = ((JavaValue)parentNode.getValueContainer()).getDescriptor();
    EnumerationChildrenRenderer renderer = EnumerationChildrenRenderer.getCurrent(descriptor);
    XDebuggerTreeNode newNode = parentNode.addTemporaryEditorNode(AllIcons.Debugger.Db_watch, null);
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
        if (lastRenderer instanceof CompoundReferenceRenderer &&
            NodeRendererSettings.getInstance().getCustomRenderers().contains((NodeRenderer)lastRenderer) &&
            !(((CompoundReferenceRenderer)lastRenderer).getChildrenRenderer() instanceof ExpressionChildrenRenderer)) {
          ((CompoundReferenceRenderer)lastRenderer).setChildrenRenderer(enumerationChildrenRenderer);
        }
        else {
          NodeRenderer renderer =
            NodeRendererSettings.getInstance().createCompoundReferenceRenderer(name, name, null, enumerationChildrenRenderer);
          renderer.setEnabled(true);
          NodeRendererSettings.getInstance().getCustomRenderers().addRenderer(renderer);
          NodeRendererSettings.getInstance().fireRenderersChanged();
        }
        return enumerationChildrenRenderer.getChildren();
      }
    }.show());
  }

  private static @Nullable String getTypeName(ValueDescriptorImpl descriptor) {
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

  @Override
  protected @Nullable Rectangle getEditorBounds() {
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
