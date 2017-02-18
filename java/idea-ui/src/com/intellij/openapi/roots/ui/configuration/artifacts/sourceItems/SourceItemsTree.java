/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.dnd.AdvancedDnDSource;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorImpl;
import com.intellij.openapi.roots.ui.configuration.artifacts.SimpleDnDAwareTree;
import com.intellij.openapi.roots.ui.configuration.artifacts.SourceItemsDraggingObject;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.actions.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.WeightBasedComparator;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class SourceItemsTree extends SimpleDnDAwareTree implements AdvancedDnDSource, Disposable{
  private final ArtifactEditorImpl myArtifactsEditor;
  private final SimpleTreeBuilder myBuilder;

  public SourceItemsTree(ArtifactEditorContext editorContext, ArtifactEditorImpl artifactsEditor) {
    myArtifactsEditor = artifactsEditor;
    myBuilder = new SimpleTreeBuilder(this, this.getBuilderModel(), new SourceItemsTreeStructure(editorContext, artifactsEditor), new WeightBasedComparator(true));
    setRootVisible(false);
    setShowsRootHandles(true);
    Disposer.register(this, myBuilder);
    PopupHandler.installPopupHandler(this, createPopupGroup(), ActionPlaces.UNKNOWN, ActionManager.getInstance());
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
    group.add(new SourceItemFindUsagesAction(this, myArtifactsEditor.getContext().getProject(), myArtifactsEditor.getContext().getParent()));

    DefaultTreeExpander expander = new DefaultTreeExpander(this);
    final CommonActionsManager commonActionsManager = CommonActionsManager.getInstance();
    group.add(Separator.getInstance());
    group.addAction(commonActionsManager.createExpandAllAction(expander, this));
    group.addAction(commonActionsManager.createCollapseAllAction(expander, this));
    return group;
  }

  public void rebuildTree() {
    myBuilder.updateFromRoot(true);
  }

  public void initTree() {
    myBuilder.initRootNode();
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
  public boolean canStartDragging(DnDAction action, Point dragOrigin) {
    return !getSelectedItems().isEmpty();
  }

  @Override
  public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
    List<PackagingSourceItem> items = getSelectedItems();
    return new DnDDragStartBean(new SourceItemsDraggingObject(items.toArray(new PackagingSourceItem[items.size()])));
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
  public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
    final DefaultMutableTreeNode[] nodes = getSelectedTreeNodes();
    if (nodes.length == 1) {
      return DnDAwareTree.getDragImage(this, TreeUtil.getPathFromRoot(nodes[0]), dragOrigin);
    }
    return DnDAwareTree.getDragImage(this, ProjectBundle.message("drag.n.drop.text.0.packaging.elements", nodes.length), dragOrigin);
  }

  @Override
  public void dragDropEnd() {
  }

  @Override
  public void dropActionChanged(int gestureModifiers) {
  }

  private static class SourceItemsTreeStructure extends SimpleTreeStructure {
    private final ArtifactEditorContext myEditorContext;
    private final ArtifactEditorImpl myArtifactsEditor;
    private SourceItemsTreeRoot myRoot;

    public SourceItemsTreeStructure(ArtifactEditorContext editorContext, ArtifactEditorImpl artifactsEditor) {
      myEditorContext = editorContext;
      myArtifactsEditor = artifactsEditor;
    }

    @Override
    public Object getRootElement() {
      if (myRoot == null) {
        myRoot = new SourceItemsTreeRoot(myEditorContext, myArtifactsEditor);
      }
      return myRoot;
    }
  }
}
