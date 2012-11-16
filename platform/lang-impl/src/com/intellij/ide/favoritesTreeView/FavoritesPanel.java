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
import com.intellij.ide.favoritesTreeView.actions.AddToFavoritesAction;
import com.intellij.ide.projectView.impl.TransferableWrapper;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

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
    myViewPanel = new FavoritesTreeViewPanel(myProject);
    myTree = myViewPanel.getTree();
    myTreeBuilder = myViewPanel.getBuilder();
    if (myTreeBuilder != null) {
      Disposer.register(myProject, myTreeBuilder);
    }
    myTreeStructure = myViewPanel.getFavoritesTreeStructure();
    setupDnD();
  }

  public FavoritesTreeViewPanel getPanel() {
    return myViewPanel;
  }

  private void setupDnD() {
    DnDSupport.createBuilder(myTree)
      .setBeanProvider(new Function<DnDActionInfo, DnDDragStartBean>() {
        @Override
        public DnDDragStartBean fun(DnDActionInfo info) {
          final TreePath path = myTree.getPathForLocation(info.getPoint().x, info.getPoint().y);
          if (path != null) {
            return new DnDDragStartBean(path);
          }
          return new DnDDragStartBean("");
        }
      })
      // todo process drag-and-drop here for tasks
      .setTargetChecker(new DnDTargetChecker() {
        @Override
        public boolean update(DnDEvent event) {
          final Object obj = event.getAttachedObject();
          if (obj instanceof TreePath) {
            event.setDropPossible(((TreePath)obj).getPathCount() > 2);
            return true;
          }

          if ("".equals(obj)) {
            event.setDropPossible(false);
            return false;
          }

          final Point p = event.getPoint();
          FavoritesListNode node = findFavoritesListNode(p);
          if (node != null) {
            TreePath pathToList = myTree.getPath(node);
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
            event.setDropPossible(true);
            return true;
          }
          event.setDropPossible(false);
          return false;
        }
      })
      .setDropHandler(new DnDDropHandler() {
        @Override
        public void drop(DnDEvent event) {
          final FavoritesListNode node = findFavoritesListNode(event.getPoint());
          final FavoritesManager mgr = FavoritesManager.getInstance(myProject);

          if (node == null) return;

          final String listTo = node.getValue();
          final Object obj = event.getAttachedObject();

          if (obj instanceof TreePath) {
            final TreePath path = (TreePath)obj;
            final String listFrom = getListNodeFromPath(path).getValue();
            if (listTo.equals(listFrom)) return;
            if (path.getPathCount() == 3) {
              final AbstractTreeNode abstractTreeNode =
                ((FavoritesTreeNodeDescriptor)((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject()).getElement();
              Object element = abstractTreeNode.getValue();
              mgr.removeRoot(listFrom, Collections.singletonList(abstractTreeNode));
              if (element instanceof SmartPsiElementPointer) {
                element = ((SmartPsiElementPointer)element).getElement();
              }
              mgr.addRoots(listTo, null, element);
            }
          }
          else if (obj instanceof TransferableWrapper) {
            final PsiElement[] elements = ((TransferableWrapper)obj).getPsiElements();
            if (elements != null && elements.length > 0) {
              ArrayList<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();
              for (PsiElement element : elements) {
                if (element instanceof SmartPsiElementPointer) {
                  element = ((SmartPsiElementPointer)element).getElement();
                }
                final Collection<AbstractTreeNode> tmp = AddToFavoritesAction
                  .createNodes(myProject, null, element, true, FavoritesManager.getInstance(myProject).getViewSettings());
                nodes.addAll(tmp);
                mgr.addRoots(listTo, nodes);
              }
              myTreeBuilder.select(nodes.toArray(), null);
            }
          }
        }
      })
      .setDisposableParent(myProject)
      .install();
  }

  @Nullable
  private TreeNode findListNode(String listName) {
    final Object root = myTree.getModel().getRoot();
    
    if (root instanceof DefaultMutableTreeNode) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)root;
      for (int i = 0; i < node.getChildCount(); i++) {
        final TreeNode listNode = node.getChildAt(i);
        if (listName.equals(listNode.toString())) {
          return listNode; 
        }
      }
    }
    return null;
  }


  @Nullable
  private FavoritesListNode findFavoritesListNode(Point point) {
    final TreePath path = myTree.getPathForLocation(point.x, point.y);
    final FavoritesListNode node = getListNodeFromPath(path);
    return node == null ? (FavoritesListNode)((FavoritesRootNode)myTreeStructure.getRootElement()).getChildren().iterator().next()
                        : node;
  }

  private static FavoritesListNode getListNodeFromPath(TreePath path) {
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
