/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.dnd.*;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.projectView.impl.TransferableWrapper;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Comparator;

/**
 * @author Konstantin Bulenkov
 */
public class FavoritesPanel {
  private Project myProject;
  private FavoritesTreeViewPanel myViewPanel;
  private DnDAwareTree myTree;
  private AbstractTreeBuilder myTreeBuilder;
  private FavoritesTreeStructure myTreeStructure;

  public FavoritesPanel(Project project) {
    myProject = project;
    myViewPanel = new FavoritesTreeViewPanel(myProject, null);
    myTree = myViewPanel.getTree();
    myTreeBuilder = myViewPanel.getBuilder();
    if (myTreeBuilder != null) {
      Disposer.register(myProject, myTreeBuilder);
      myTreeBuilder.setNodeDescriptorComparator(new Comparator<NodeDescriptor>() {
        @Override
        public int compare(NodeDescriptor nd1, NodeDescriptor nd2) {
          if (nd1 instanceof FavoritesTreeNodeDescriptor && nd2 instanceof FavoritesTreeNodeDescriptor) {
            FavoritesTreeNodeDescriptor fd1 = (FavoritesTreeNodeDescriptor)nd1;
            FavoritesTreeNodeDescriptor fd2 = (FavoritesTreeNodeDescriptor)nd2;
            return 0;//super.compare(fd1.getElement(), fd2.getElement()); todo
          }
          return 0;
        }
      });
    }
    myTreeStructure = myViewPanel.getFavoritesTreeStructure();
    setupDnD();
  }

  public JComponent getPanel() {
    return myViewPanel;
  }

  private void setupDnD() {
    DnDSupport.createBuilder(myTree)
      .setBeanProvider(new Function<DnDActionInfo, DnDDragStartBean>() {
        @Override
        public DnDDragStartBean fun(DnDActionInfo dnDActionInfo) {
          return new DnDDragStartBean("");
        }
      })
      .setTargetChecker(new DnDTargetChecker() {
        @Override
        public boolean update(DnDEvent event) {
          final Point p = event.getPoint();

          FavoritesListNode node = findFavoritesListNode(p);
          if (node != null) {
            TreePath pathToList = myTree.getPathForLocation(p.x, p.y);
            while (pathToList != null) {
              final Object pathObj = pathToList.getLastPathComponent();
              if (pathObj instanceof DefaultMutableTreeNode) {
                final Object userObject = ((DefaultMutableTreeNode)pathObj).getUserObject();
                if (userObject instanceof FavoritesTreeNodeDescriptor) {
                  if (((FavoritesTreeNodeDescriptor)userObject).getElement() == node) {
                    break;
                  }
                }
              }
              pathToList = pathToList.getParentPath();
            }
            if (pathToList != null) {
              Rectangle bounds = myTree.getPathBounds(pathToList);
              if (bounds != null) {
                event.setHighlighting(new RelativeRectangle(myTree, bounds), DnDEvent.DropTargetHighlightingType.RECTANGLE);
              }
            }
            event.setDropPossible(true, null);
            return true;
          }
          event.setDropPossible(false, null);
          return false;
        }
      })
      .setDropHandler(new DnDDropHandler() {
        @Override
        public void drop(DnDEvent event) {
          final Object obj = event.getAttachedObject();
          if (obj instanceof TransferableWrapper) {
            final PsiElement[] elements = ((TransferableWrapper)obj).getPsiElements();
            final FavoritesListNode node = findFavoritesListNode(event.getPoint());
            if (elements != null && elements.length > 0 && node != null) {
              FavoritesManager.getInstance(myProject).addRoots(node.getValue(), null, elements);
            }
          }
        }
      })
      .setDisposableParent(myProject)
      .install();
  }

  @Nullable
  private FavoritesListNode findFavoritesListNode(Point point) {
    final TreePath path = myTree.getPathForLocation(point.x, point.y);
    if (path != null && path.getPathCount() > 1) {
      final Object o = path.getPath()[1];
      if (o instanceof DefaultMutableTreeNode) {
        final Object obj = ((DefaultMutableTreeNode)o).getUserObject();
        if (obj instanceof FavoritesTreeNodeDescriptor) {
          final AbstractTreeNode node = ((FavoritesTreeNodeDescriptor)obj).getElement();
          if (node instanceof FavoritesListNode) {
            return (FavoritesListNode)node;
          }
        }
      }
    }
    return null;
  }
}
