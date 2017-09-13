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
package com.intellij.ide.projectView.impl;

import com.intellij.ProjectTopics;
import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarksListener;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import java.util.Comparator;

import static com.intellij.ide.util.treeView.TreeState.VISIT;
import static com.intellij.util.ui.UIUtil.putClientProperty;

class AsyncProjectViewSupport {
  private static final Logger LOG = Logger.getInstance(AbstractProjectViewPSIPane.class);
  private final StructureTreeModel myStructureTreeModel;
  private final AsyncTreeModel myAsyncTreeModel;
  private final Project myProject;

  public AsyncProjectViewSupport(Disposable parent,
                                 Project project,
                                 JTree tree,
                                 AbstractTreeStructure structure,
                                 Comparator<NodeDescriptor> comparator) {
    myStructureTreeModel = new StructureTreeModel(true);
    myStructureTreeModel.setStructure(structure);
    myStructureTreeModel.setComparator(comparator);
    myAsyncTreeModel = new AsyncTreeModel(myStructureTreeModel, true);
    myAsyncTreeModel.setRootImmediately(myStructureTreeModel.getRootImmediately());
    myProject = project;
    tree.setModel(myAsyncTreeModel);
    Disposer.register(parent, myAsyncTreeModel);
    putClientProperty(tree, VISIT, visitor -> myAsyncTreeModel.visit(visitor, true));
    MessageBusConnection connection = project.getMessageBus().connect(parent);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        updateAll();
      }
    });
    connection.subscribe(BookmarksListener.TOPIC, new BookmarksListener() {
      @Override
      public void bookmarkAdded(@NotNull Bookmark bookmark) {
        updateByFile(bookmark.getFile());
      }

      @Override
      public void bookmarkRemoved(@NotNull Bookmark bookmark) {
        updateByFile(bookmark.getFile());
      }

      @Override
      public void bookmarkChanged(@NotNull Bookmark bookmark) {
        updateByFile(bookmark.getFile());
      }

      @Override
      public void bookmarksOrderChanged() {
        //do nothing
      }
    });
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeListener() {
      @Override
      public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
        updateAll();//TODO:
      }

      @Override
      public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
        updateAll();//TODO:
      }

      @Override
      public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
        updateAll();//TODO:
      }

      @Override
      public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
        updateAll();//TODO:
      }

      @Override
      public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
        updateAll();//TODO:
      }

      @Override
      public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
        updateAll();//TODO:
      }

      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        updateAll();//TODO:
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        updateAll();//TODO:
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        updateAll();//TODO:
      }

      @Override
      public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        updateAll();//TODO:
      }

      @Override
      public void childMoved(@NotNull PsiTreeChangeEvent event) {
        updateAll();//TODO:
      }

      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        updateAll();//TODO:
      }
    }, parent);
    FileStatusManager.getInstance(project).addFileStatusListener(new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        updateAll();
      }

      @Override
      public void fileStatusChanged(@NotNull VirtualFile file) {
        updateByFile(file);
      }
    }, parent);
    CopyPasteManager.getInstance().addContentChangedListener(new CopyPasteUtil.DefaultCopyPasteListener(this::updateByElement), parent);
    WolfTheProblemSolver.getInstance(project).addProblemListener(new WolfTheProblemSolver.ProblemListener() {
      @Override
      public void problemsAppeared(@NotNull VirtualFile file) {
        updateByFile(file);
      }

      @Override
      public void problemsDisappeared(@NotNull VirtualFile file) {
        updateByFile(file);
      }
    }, parent);
  }

  public void select(JTree tree, Object object, VirtualFile file) {
    if (object instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)object;
      object = node.getValue();
      LOG.debug("select AbstractTreeNode");
    }
    if (object instanceof PsiElement) {
      myAsyncTreeModel.visit(new ElementFinder((PsiElement)object)).done(path1 -> {
        if (path1 != null) {
          TreeUtil.selectPath(tree, path1);
        }
        else {
          PsiElement element = PsiManager.getInstance(myProject).findFile(file);
          if (element != null) {
            myAsyncTreeModel.visit(new ElementFinder(element)).done(path2 -> {
              if (path2 != null) {
                TreeUtil.selectPath(tree, path2);
              }
            });
          }
        }
      });
    }
    else {
      LOG.debug("select unexpected object ", (object == null ? null : object.getClass()));
    }
  }

  public void updateAll() {
    if (myStructureTreeModel != null) {
      myStructureTreeModel.invalidate();
    }
  }

  public void updateByFile(@NotNull VirtualFile file) {
    if (myStructureTreeModel != null) {
      myStructureTreeModel.invalidate(); //TODO:
    }
  }

  public void updateByElement(@NotNull PsiElement element) {
    if (myStructureTreeModel != null) {
      myStructureTreeModel.invalidate(); //TODO:
    }
  }

  private static VirtualFile getVirtualFile(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file == null ? null : file.getVirtualFile();
  }

  /**
   * @param object  a node of a tree structure
   * @param element an element to find
   * @return {@code true} if the specified node represents the given element
   */
  protected boolean found(Object object, @NotNull PsiElement element) {
    if (object instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)object;
      return node.canRepresent(element);
    }
    LOG.debug("found unexpected object ", (object == null ? null : object.getClass()));
    return false;
  }

  /**
   * @param object  a node of a tree structure
   * @param element an element to find
   * @return {@code true} if the specified node is an ancestor of the given element
   */
  protected boolean contains(Object object, @NotNull PsiElement element) {
    VirtualFile file = getVirtualFile(element);
    if (file == null) {
      LOG.debug("no virtual file for element ", element);
    }
    else if (object instanceof ProjectViewNode) {
      ProjectViewNode node = (ProjectViewNode)object;
      if (node.contains(file)) return true;
    }
    if (object instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)object;
      Object value = node.getValue();
      return value instanceof PsiElement && PsiTreeUtil.isAncestor((PsiElement)value, element, true);
    }
    LOG.debug("contains unexpected object ", (object == null ? null : object.getClass()));
    return false;
  }

  private class ElementFinder implements TreeVisitor {
    private final SmartPsiElementPointer<PsiElement> pointer;

    private ElementFinder(@NotNull PsiElement element) {
      pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element);
    }

    @NotNull
    @Override
    public Action accept(@NotNull TreePath path) {
      LOG.debug("process ", path);
      PsiElement element = pointer.getElement();
      if (element == null) return Action.SKIP_SIBLINGS;
      Object component = path.getLastPathComponent();
      if (component instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)component;
        Object object = node.getUserObject();
        if (found(object, element)) return Action.INTERRUPT;
        if (contains(object, element)) return Action.CONTINUE;
      }
      return Action.SKIP_CHILDREN;
    }
  }
}
