// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.dnd.AdvancedDnDSource;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.CompositePackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.RenameablePackagingElement;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreePathUtil;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class LayoutTree extends SimpleDnDAwareTree implements AdvancedDnDSource {
  private static final Logger LOG = Logger.getInstance(LayoutTree.class);
  private final ArtifactEditorImpl myArtifactsEditor;
  private final StructureTreeModel<?> myTreeModel;

  public LayoutTree(ArtifactEditorImpl artifactsEditor, StructureTreeModel<?> treeModel) {
    myArtifactsEditor = artifactsEditor;
    myTreeModel = treeModel;
    setRootVisible(true);
    setShowsRootHandles(false);
    setCellEditor(new LayoutTreeCellEditor());
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      DnDManager.getInstance().registerSource(this);
    }

    //todo fix for tooltips in the tree. Otherwise tooltips will be ignored by DnDEnabled
    setToolTipText("");
  }

  @Override
  protected void configureUiHelper(TreeUIHelper helper) {
    final Function<TreePath, String> f = path -> {
      final SimpleNode node = getNodeFor(path);
      if (node instanceof PackagingElementNode) {
        return ((PackagingElementNode<?>)node).getElementPresentation().getSearchName();
      }
      return "";
    };
    TreeSpeedSearch.installOn(this, true, f);
  }

  private List<PackagingElementNode<?>> getNodesToDrag() {
    return getSelection().getNodes();
  }

  @Override
  public boolean canStartDragging(DnDAction action, @NotNull Point dragOrigin) {
    return !getNodesToDrag().isEmpty();
  }

  @Override
  public DnDDragStartBean startDragging(DnDAction action, @NotNull Point dragOrigin) {
    return new DnDDragStartBean(new LayoutNodesDraggingObject(myArtifactsEditor, getNodesToDrag()));
  }

  @Override
  public @Nullable Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin, @NotNull DnDDragStartBean bean) {
    final List<PackagingElementNode<?>> nodes = getNodesToDrag();
    if (nodes.size() == 1) {
      return DnDAwareTree.getDragImage(this, getPathFor(nodes.get(0)), dragOrigin);
    }
    return DnDAwareTree.getDragImage(this, JavaUiBundle.message("drag.n.drop.text.0.packaging.elements", nodes.size()), dragOrigin);
  }

  @Override
  public void dispose() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      DnDManager.getInstance().unregisterSource(this);
    }
  }

  public LayoutTreeSelection getSelection() {
    return new LayoutTreeSelection(this);
  }

  @Nullable
  public PackagingElement<?> getElementByPath(TreePath path) {
    final SimpleNode node = getNodeFor(path);
    if (node instanceof PackagingElementNode) {
      final List<? extends PackagingElement<?>> elements = ((PackagingElementNode<?>)node).getPackagingElements();
      if (!elements.isEmpty()) {
        return elements.get(0);
      }
    }
    return null;
  }

  public void addSubtreeToUpdate(final PackagingElementNode elementNode) {
    myTreeModel.invalidate(elementNode, true);
  }

  public TreeVisitor createVisitorCompositeNodeChild(String parentPath, Predicate<? super PackagingElementNode<?>> childFilter) {
    List<Predicate<PackagingElementNode<?>>> parentElementFilters = ContainerUtil.map(StringUtil.split(parentPath, "/"),
                                                                                      LayoutTree::createCompositeNodeByNameFilter);
    TreePath predicatesPath = TreePathUtil.convertCollectionToTreePath(ContainerUtil.append(parentElementFilters, childFilter));
    return new TreeVisitor.ByTreePath<PackagingElementNode<?>>(true, predicatesPath, node -> TreeUtil.getUserObject(PackagingElementNode.class, node)) {
      @Override
      protected boolean matches(@NotNull PackagingElementNode<?> pathComponent, @NotNull Object thisComponent) {
        //noinspection unchecked
        return thisComponent instanceof Predicate && ((Predicate<PackagingElementNode<?>>)thisComponent).test(pathComponent);
      }
    };
  }

  @NotNull
  private static Predicate<PackagingElementNode<?>> createCompositeNodeByNameFilter(String name) {
    return (PackagingElementNode<?> node) -> node instanceof CompositePackagingElementNode
                                             && ((CompositePackagingElementNode)node).getFirstElement().getName().equals(name);
  }

  private class LayoutTreeCellEditor extends DefaultCellEditor {
    LayoutTreeCellEditor() {
      super(new JTextField());
    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
      final JTextField field = (JTextField)super.getTreeCellEditorComponent(tree, value, isSelected, expanded, leaf, row);
      final Object node = ((DefaultMutableTreeNode)value).getUserObject();
      final PackagingElement<?> element = ((PackagingElementNode<?>)node).getElementIfSingle();
      LOG.assertTrue(element != null);
      final String name = ((RenameablePackagingElement)element).getName();
      field.setText(name);
      int i = name.lastIndexOf('.');
      field.setSelectionStart(0);
      field.setSelectionEnd(i != -1 ? i : name.length());
      return field;
    }

    @Override
    public boolean stopCellEditing() {
      final String newValue = ((JTextField)editorComponent).getText();
      final TreePath path = getEditingPath();
      final Object node = getNodeFor(path);
      RenameablePackagingElement currentElement = null;
      if (node instanceof PackagingElementNode) {
        final PackagingElement<?> element = ((PackagingElementNode<?>)node).getElementIfSingle();
        if (element instanceof RenameablePackagingElement) {
          currentElement = (RenameablePackagingElement)element;
        }
      }
      final boolean stopped = super.stopCellEditing();
      if (stopped && currentElement != null) {
        final RenameablePackagingElement finalCurrentElement = currentElement;
        myArtifactsEditor.getLayoutTreeComponent().editLayout(() -> finalCurrentElement.rename(newValue));
        myArtifactsEditor.queueValidation();
        myArtifactsEditor.getLayoutTreeComponent().updatePropertiesPanel(true);
        addSubtreeToUpdate((PackagingElementNode)node);
        requestFocusToTree();
      }
      return stopped;
    }

    @Override
    public void cancelCellEditing() {
      super.cancelCellEditing();
      requestFocusToTree();
    }

    private void requestFocusToTree() {
      IdeFocusManager.getInstance(myArtifactsEditor.getContext().getProject()).requestFocus(LayoutTree.this, true);
    }
  }
}
