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
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
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
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.util.SmartList;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import java.util.Comparator;
import java.util.List;

import static com.intellij.ide.util.treeView.TreeState.VISIT;
import static com.intellij.util.ui.UIUtil.putClientProperty;

class AsyncProjectViewSupport {
  private static final Logger LOG = Logger.getInstance(AbstractProjectViewPSIPane.class);
  private final StructureTreeModel myStructureTreeModel;
  private final AsyncTreeModel myAsyncTreeModel;
  private final PsiManager myPsiManager;

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
    myPsiManager = PsiManager.getInstance(project);
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
    myPsiManager.addPsiTreeChangeListener(new ProjectViewPsiTreeChangeListener(project) {
      @Override
      protected boolean isFlattenPackages() {
        return structure instanceof AbstractProjectTreeStructure && ((AbstractProjectTreeStructure)structure).isFlattenPackages();
      }

      @Override
      protected AbstractTreeUpdater getUpdater() {
        return null;
      }

      @Override
      protected DefaultMutableTreeNode getRootNode() {
        return null;
      }

      @Override
      protected void addSubtreeToUpdateByRoot() {
        updateAll();
      }

      @Override
      protected boolean addSubtreeToUpdateByElement(PsiElement element) {
        updateByElement(element);
        return true;
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
      selectElement(tree, (PsiElement)object, () -> {
        PsiElement element = myPsiManager.findFile(file);
        if (element != null) selectElement(tree, element, () -> LOG.debug("cannot find element"));
      });
    }
    else {
      LOG.debug("select unexpected object ", (object == null ? null : object.getClass()));
    }
  }

  private void selectElement(JTree tree, @NotNull PsiElement element, Runnable ifNotFound) {
    myAsyncTreeModel.visit(new ProjectViewNodeVisitor(element)).done(path -> {
      if (path != null) {
        TreeUtil.selectPath(tree, path);
      }
      else if (ifNotFound != null) {
        ifNotFound.run();
      }
    });
  }

  public void updateAll() {
    LOG.debug(new RuntimeException("reload a whole tree"));
    myStructureTreeModel.invalidate();
  }

  public void update(@NotNull TreePath path) {
    myStructureTreeModel.invalidate(path);
  }

  public void update(@NotNull List<TreePath> list) {
    for (TreePath path : list) update(path);
  }

  public void updateByFile(@NotNull VirtualFile file) {
    PsiElement element = myPsiManager.findFile(file);
    if (element != null) updateByElement(element);
  }

  public void updateByElement(@NotNull PsiElement element) {
    SmartList<TreePath> list = new SmartList<>();
    myAsyncTreeModel.visit(new ProjectViewNodeVisitor(element, path -> !list.add(path)), false).done(path -> update(list));
  }
}
