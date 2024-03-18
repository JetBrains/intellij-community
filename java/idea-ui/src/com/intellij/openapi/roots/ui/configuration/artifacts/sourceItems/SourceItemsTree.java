// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.dnd.AdvancedDnDSource;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorImpl;
import com.intellij.openapi.roots.ui.configuration.artifacts.SimpleDnDAwareTree;
import com.intellij.openapi.roots.ui.configuration.artifacts.SourceItemsDraggingObject;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.actions.*;
import com.intellij.openapi.util.Pair;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.WeightBasedComparator;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SourceItemsTree extends SimpleDnDAwareTree implements AdvancedDnDSource, Disposable{
  private final ArtifactEditorImpl myArtifactsEditor;
  private final StructureTreeModel<SourceItemsTreeStructure> myStructureTreeModel;
  private final SourceItemsTreeStructure myTreeStructure;

  public SourceItemsTree(ArtifactEditorContext editorContext, ArtifactEditorImpl artifactsEditor) {
    myArtifactsEditor = artifactsEditor;
    myTreeStructure = new SourceItemsTreeStructure(editorContext, artifactsEditor);
    myStructureTreeModel = new StructureTreeModel<>(myTreeStructure, new WeightBasedComparator(true), this);
    setModel(new AsyncTreeModel(myStructureTreeModel, this));
    setRootVisible(false);
    setShowsRootHandles(true);
    PopupHandler.installPopupMenu(this, createPopupGroup(), "ArtifactSourceItemTreePopup");
    installDnD();
  }

  private void installDnD() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      DnDManager.getInstance().registerSource(this);
    }
  }

  private ActionGroup createPopupGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new PutSourceItemIntoDefaultLocationAction(this, myArtifactsEditor));
    group.add(new PackAndPutIntoDefaultLocationAction(this, myArtifactsEditor));
    group.add(new PutSourceItemIntoParentAndLinkViaManifestAction(this, myArtifactsEditor));
    group.add(new ExtractIntoDefaultLocationAction(this, myArtifactsEditor));

    group.add(Separator.getInstance());
    group.add(new SourceItemNavigateAction(this));
    group.add(new SourceItemFindUsagesAction(this, myArtifactsEditor.getContext().getParent()));

    DefaultTreeExpander expander = new DefaultTreeExpander(this);
    final CommonActionsManager commonActionsManager = CommonActionsManager.getInstance();
    group.add(Separator.getInstance());
    group.addAction(commonActionsManager.createExpandAllAction(expander, this));
    group.addAction(commonActionsManager.createCollapseAllAction(expander, this));
    return group;
  }

  public void rebuildTree() {
    myTreeStructure.clearCaches();
    myStructureTreeModel.invalidateAsync();
  }

  @Override
  public void dispose() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      DnDManager.getInstance().unregisterSource(this);
    }
  }

  private DefaultMutableTreeNode[] getSelectedTreeNodes() {
    return getSelectedNodes(DefaultMutableTreeNode.class, null);
  }

  @Override
  public boolean canStartDragging(DnDAction action, @NotNull Point dragOrigin) {
    return !getSelectedItems().isEmpty();
  }

  @Override
  public DnDDragStartBean startDragging(DnDAction action, @NotNull Point dragOrigin) {
    List<PackagingSourceItem> items = getSelectedItems();
    return new DnDDragStartBean(new SourceItemsDraggingObject(items.toArray(new PackagingSourceItem[0])));
  }

  public List<SourceItemNode> getSelectedSourceItemNodes() {
    final List<SourceItemNode> nodes = new ArrayList<>();
    for (DefaultMutableTreeNode treeNode : getSelectedTreeNodes()) {
      final Object userObject = treeNode.getUserObject();
      if (userObject instanceof SourceItemNode) {
        nodes.add((SourceItemNode)userObject);
      }
    }
    return nodes;
  }

  public List<PackagingSourceItem> getSelectedItems() {
    List<PackagingSourceItem> items = new ArrayList<>();
    for (SourceItemNode node : getSelectedSourceItemNodes()) {
      final PackagingSourceItem sourceItem = node.getSourceItem();
      if (sourceItem != null && sourceItem.isProvideElements()) {
        items.add(sourceItem);
      }
    }
    return items;
  }

  @Override
  public @Nullable Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin, @NotNull DnDDragStartBean bean) {
    final DefaultMutableTreeNode[] nodes = getSelectedTreeNodes();
    if (nodes.length == 1) {
      return DnDAwareTree.getDragImage(this, TreeUtil.getPathFromRoot(nodes[0]), dragOrigin);
    }
    return DnDAwareTree.getDragImage(this, JavaUiBundle.message("drag.n.drop.text.0.packaging.elements", nodes.length), dragOrigin);
  }

  private static class SourceItemsTreeStructure extends SimpleTreeStructure {
    private final ArtifactEditorContext myEditorContext;
    private final ArtifactEditorImpl myArtifactsEditor;
    private SourceItemsTreeRoot myRoot;

    SourceItemsTreeStructure(ArtifactEditorContext editorContext, ArtifactEditorImpl artifactsEditor) {
      myEditorContext = editorContext;
      myArtifactsEditor = artifactsEditor;
    }

    @NotNull
    @Override
    public Object getRootElement() {
      if (myRoot == null) {
        myRoot = new SourceItemsTreeRoot(myEditorContext, myArtifactsEditor);
      }
      return myRoot;
    }
  }
}
