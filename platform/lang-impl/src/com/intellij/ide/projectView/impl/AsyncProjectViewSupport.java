// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.RestoreSelectionListener;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeCollector;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.tree.ProjectFileChangeListener;
import com.intellij.util.SmartList;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.intellij.ide.util.treeView.TreeState.expand;
import static com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES;

class AsyncProjectViewSupport {
  private static final Logger LOG = Logger.getInstance(AsyncProjectViewSupport.class);
  private final TreeCollector<VirtualFile> myFileRoots = TreeCollector.createFileRootsCollector();
  private final ProjectFileChangeListener myChangeListener;
  private final StructureTreeModel myStructureTreeModel;
  private final AsyncTreeModel myAsyncTreeModel;

  public AsyncProjectViewSupport(Disposable parent,
                                 Project project,
                                 JTree tree,
                                 AbstractTreeStructure structure,
                                 Comparator<NodeDescriptor> comparator) {
    myStructureTreeModel = new StructureTreeModel(true);
    myStructureTreeModel.setStructure(structure);
    myStructureTreeModel.setComparator(comparator);
    myAsyncTreeModel = new AsyncTreeModel(myStructureTreeModel, true, parent);
    myAsyncTreeModel.setRootImmediately(myStructureTreeModel.getRootImmediately());
    myChangeListener = new ProjectFileChangeListener(myStructureTreeModel.getInvoker(), project, (module, file) -> {
      if (myFileRoots.add(file)) {
        myFileRoots.processLater(myStructureTreeModel.getInvoker(), roots -> roots.forEach(root -> updateByFile(root, true)));
      }
    });
    setModel(tree, myAsyncTreeModel);
    MessageBusConnection connection = project.getMessageBus().connect(parent);
    connection.subscribe(VFS_CHANGES, myChangeListener);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        updateAll(null);
      }
    });
    connection.subscribe(BookmarksListener.TOPIC, new BookmarksListener() {
      @Override
      public void bookmarkAdded(@NotNull Bookmark bookmark) {
        updateByFile(bookmark.getFile(), false);
      }

      @Override
      public void bookmarkRemoved(@NotNull Bookmark bookmark) {
        updateByFile(bookmark.getFile(), false);
      }

      @Override
      public void bookmarkChanged(@NotNull Bookmark bookmark) {
        updateByFile(bookmark.getFile(), false);
      }
    });
    PsiManager.getInstance(project).addPsiTreeChangeListener(new ProjectViewPsiTreeChangeListener(project) {
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
        updateAll(null);
      }

      @Override
      protected boolean addSubtreeToUpdateByElement(PsiElement element) {
        VirtualFile file = PsiUtilCore.getVirtualFile(element);
        if (file != null) {
          myChangeListener.invalidate(file);
        }
        else {
          updateByElement(element, true);
        }
        return true;
      }
    }, parent);
    FileStatusManager.getInstance(project).addFileStatusListener(new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        updateAllPresentations();
      }

      @Override
      public void fileStatusChanged(@NotNull VirtualFile file) {
        updateByFile(file, false);
      }
    }, parent);
    CopyPasteManager.getInstance().addContentChangedListener(new CopyPasteUtil.DefaultCopyPasteListener(element -> updateByElement(element, true)), parent);
    WolfTheProblemSolver.getInstance(project).addProblemListener(new WolfTheProblemSolver.ProblemListener() {
      @Override
      public void problemsAppeared(@NotNull VirtualFile file) {
        updatePresentationsFromRootTo(file);
      }

      @Override
      public void problemsDisappeared(@NotNull VirtualFile file) {
        updatePresentationsFromRootTo(file);
      }
    }, parent);
  }

  public void setComparator(Comparator<NodeDescriptor> comparator) {
    myStructureTreeModel.setComparator(comparator);
  }

  public void select(JTree tree, Object object, VirtualFile file) {
    if (object instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)object;
      object = node.getValue();
      LOG.debug("select AbstractTreeNode");
    }
    PsiElement element = object instanceof PsiElement ? (PsiElement)object : null;
    LOG.debug("select object: ", object, " in file: ", file);
    TreeVisitor visitor = AbstractProjectViewPane.createVisitor(element, file);
    if (visitor != null) {
      //noinspection CodeBlock2Expr
      expand(tree, promise -> {
        myAsyncTreeModel
          .accept(visitor)
          .onProcessed(path -> {
            if (selectPath(tree, path) || element == null || file == null || Registry.is("async.project.view.support.extra.select.disabled")) {
              promise.setResult(null);
            }
            else {
              // try to search the specified file instead of element,
              // because Kotlin files cannot represent containing functions
              myAsyncTreeModel
                .accept(AbstractProjectViewPane.createVisitor(file))
                .onProcessed(path2 -> {
                  selectPath(tree, path2);
                  promise.setResult(null);
                });
            }
          });
      });
    }
  }

  private static boolean selectPath(@NotNull JTree tree, TreePath path) {
    if (path == null) return false;
    tree.expandPath(path); // request to expand found path
    TreeUtil.selectPath(tree, path); // select and scroll to center
    return true;
  }

  public void updateAll(Runnable onDone) {
    LOG.debug(new RuntimeException("reload a whole tree"));
    myStructureTreeModel.invalidate(onDone == null ? null : () -> myAsyncTreeModel.onValidThread(onDone));
  }

  public void update(@NotNull TreePath path, boolean structure) {
    myStructureTreeModel.invalidate(path, structure);
  }

  public void update(@NotNull List<TreePath> list, boolean structure) {
    for (TreePath path : list) update(path, structure);
  }

  public void updateByFile(@NotNull VirtualFile file, boolean structure) {
    LOG.debug(structure ? "updateChildrenByFile: " : "updatePresentationByFile: ", file);
    update(null, file, structure);
  }

  public void updateByElement(@NotNull PsiElement element, boolean structure) {
    LOG.debug(structure ? "updateChildrenByElement: " : "updatePresentationByElement: ", element);
    update(element, null, structure);
  }

  private void update(PsiElement element, VirtualFile file, boolean structure) {
    SmartList<TreePath> list = new SmartList<>();
    acceptAndUpdate(AbstractProjectViewPane.createVisitor(element, file, path -> !list.add(path)), list, structure);
  }

  private void acceptAndUpdate(TreeVisitor visitor, List<TreePath> list, boolean structure) {
    if (visitor != null) {
      myAsyncTreeModel.accept(visitor, false)
                      .onSuccess(path -> update(list, structure));
    }
  }

  private void updatePresentationsFromRootTo(@NotNull VirtualFile file) {
    SmartList<TreePath> list = new SmartList<>();
    acceptAndUpdate(new ProjectViewFileVisitor(file, null) {
      @NotNull
      @Override
      protected Action visit(@NotNull TreePath path, @NotNull AbstractTreeNode node, @NotNull VirtualFile element) {
        Action action = super.visit(path, node, element);
        if (action != Action.SKIP_CHILDREN) list.add(path);
        return action;
      }
    }, list, false);
  }

  private void updateAllPresentations() {
    SmartList<TreePath> list = new SmartList<>();
    acceptAndUpdate(new TreeVisitor() {
      @NotNull
      @Override
      public Action visit(@NotNull TreePath path) {
        list.add(path);
        return Action.CONTINUE;
      }
    }, list, false);
  }

  static List<TreeVisitor> createVisitors(Iterable<Object> iterable) {
    if (iterable == null) return Collections.emptyList();
    List<TreeVisitor> visitors = new SmartList<>();
    for (Object object : iterable) {
      if (object instanceof AbstractTreeNode) {
        AbstractTreeNode node = (AbstractTreeNode)object;
        object = node.getValue();
      }
      TreeVisitor visitor = AbstractProjectViewPane.createVisitor(object);
      if (visitor != null) visitors.add(visitor);
    }
    return visitors;
  }

  private static void setModel(@NotNull JTree tree, @NotNull AsyncTreeModel model) {
    RestoreSelectionListener listener = new RestoreSelectionListener();
    tree.addTreeSelectionListener(listener);
    tree.setModel(model);
    Disposer.register(model, () -> {
      tree.setModel(null);
      tree.removeTreeSelectionListener(listener);
    });
  }
}