// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.bookmark.BookmarksListener;
import com.intellij.ide.bookmark.FileBookmarksListener;
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.projectView.impl.ProjectViewPaneSelectionHelper.SelectionDescriptor;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.ProblemListener;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.tree.*;
import com.intellij.ui.tree.project.ProjectFileNodeUpdater;
import com.intellij.util.SmartList;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.intellij.ide.util.treeView.TreeState.expand;
import static com.intellij.ui.tree.project.ProjectFileNode.findArea;

@ApiStatus.Internal
public class AsyncProjectViewSupport {
  private static final Logger LOG = Logger.getInstance(AsyncProjectViewSupport.class);
  private final ProjectFileNodeUpdater myNodeUpdater;
  private final StructureTreeModel myStructureTreeModel;
  private final AsyncTreeModel myAsyncTreeModel;

  public AsyncProjectViewSupport(@NotNull Disposable parent,
                          @NotNull Project project,
                          @NotNull AbstractTreeStructure structure,
                          @NotNull Comparator<NodeDescriptor<?>> comparator) {
    myStructureTreeModel = new StructureTreeModel<>(structure, comparator, parent);
    myAsyncTreeModel = new AsyncTreeModel(myStructureTreeModel, parent);
    myNodeUpdater = new ProjectFileNodeUpdater(project, myStructureTreeModel.getInvoker()) {
      @Override
      protected void updateStructure(boolean fromRoot, @NotNull Set<? extends VirtualFile> updatedFiles) {
        if (fromRoot) {
          updateAll(null);
        }
        else {
          long time = System.currentTimeMillis();
          LOG.debug("found ", updatedFiles.size(), " changed files");
          TreeCollector<VirtualFile> collector = TreeCollector.VirtualFileRoots.create();
          for (VirtualFile file : updatedFiles) {
            if (!file.isDirectory()) file = file.getParent();
            if (file != null && findArea(file, project) != null) collector.add(file);
          }
          List<VirtualFile> roots = collector.get();
          LOG.debug("found ", roots.size(), " roots in ", System.currentTimeMillis() - time, "ms");
          myStructureTreeModel.getInvoker().invoke(() -> roots.forEach(root -> updateByFile(root, true)));
        }
      }
    };
    MessageBusConnection connection = project.getMessageBus().connect(parent);
    connection.subscribe(BookmarksListener.TOPIC, new FileBookmarksListener(file -> updateByFile(file, !file.isDirectory())));
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
        myNodeUpdater.updateFromRoot();
      }

      @Override
      protected boolean addSubtreeToUpdateByElement(@NotNull PsiElement element) {
        VirtualFile file = PsiUtilCore.getVirtualFile(element);
        if (file != null) {
          myNodeUpdater.updateFromFile(file);
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
    CopyPasteUtil.addDefaultListener(parent, element -> updateByElement(element, false));
    project.getMessageBus().connect(parent).subscribe(ProblemListener.TOPIC, new ProblemListener() {
      @Override
      public void problemsAppeared(@NotNull VirtualFile file) {
        updatePresentationsFromRootTo(file);
      }

      @Override
      public void problemsDisappeared(@NotNull VirtualFile file) {
        updatePresentationsFromRootTo(file);
      }
    });
  }

  public AsyncTreeModel getTreeModel() {
    return myAsyncTreeModel;
  }

  public void setComparator(@NotNull Comparator<? super NodeDescriptor<?>> comparator) {
    myStructureTreeModel.setComparator(comparator);
  }

  public ActionCallback select(JTree tree, Object object, VirtualFile file) {
    if (object instanceof AbstractTreeNode node) {
      object = node.getValue();
      LOG.debug("select AbstractTreeNode");
    }
    PsiElement element = object instanceof PsiElement ? (PsiElement)object : null;
    LOG.debug("select object: ", object, " in file: ", file);
    SmartList<TreePath> pathsToSelect = new SmartList<>();
    TreeVisitor visitor = AbstractProjectViewPane.createVisitor(element, file, pathsToSelect);
    if (visitor == null) return ActionCallback.DONE;

    ActionCallback callback = new ActionCallback();
    //noinspection CodeBlock2Expr
    myNodeUpdater.updateImmediately(() -> expand(tree, promise -> {
      promise.onSuccess(o -> callback.setDone());
      acceptOnEDT(visitor, () -> {
        if (selectPaths(tree, pathsToSelect, visitor) ||
            element == null ||
            file == null ||
            Registry.is("async.project.view.support.extra.select.disabled")) {
          promise.setResult(null);
        }
        else {
          // try to search the specified file instead of element,
          // because Kotlin files cannot represent containing functions
          pathsToSelect.clear();
          TreeVisitor fileVisitor = AbstractProjectViewPane.createVisitor(null, file, pathsToSelect);
          acceptOnEDT(fileVisitor, () -> {
            selectPaths(tree, pathsToSelect, fileVisitor);
            promise.setResult(null);
          });
        }
      });
    }));
    return callback;
  }

  private void acceptOnEDT(@NotNull TreeVisitor visitor, @NotNull Runnable task) {
    myAsyncTreeModel.accept(visitor).onProcessed(path -> myAsyncTreeModel.onValidThread(task));
  }

  private static boolean selectPaths(@NotNull JTree tree, @NotNull List<TreePath> paths, @NotNull TreeVisitor visitor) {
    if (paths.isEmpty()) return false;
    if (paths.size() > 1) {
      if (visitor instanceof ProjectViewNodeVisitor nodeVisitor) {
        return selectPaths(tree, new SelectionDescriptor(nodeVisitor.getElement(), nodeVisitor.getFile(), paths));
      }
      if (visitor instanceof ProjectViewFileVisitor fileVisitor) {
        return selectPaths(tree, new SelectionDescriptor(null, fileVisitor.getElement(), paths));
      }
    }
    TreePath path = paths.get(0);
    tree.expandPath(path); // request to expand found path
    TreeUtil.selectPaths(tree, path); // select and scroll to center
    return true;
  }

  private static boolean selectPaths(@NotNull JTree tree, @NotNull SelectionDescriptor selectionDescriptor) {
    List<? extends TreePath> adjustedPaths = ProjectViewPaneSelectionHelper.getAdjustedPaths(selectionDescriptor);
    adjustedPaths.forEach(it -> tree.expandPath(it));
    TreeUtil.selectPaths(tree, adjustedPaths);
    return true;
  }

  public void updateAll(Runnable onDone) {
    LOG.debug(new RuntimeException("reload a whole tree"));
    CompletableFuture<?> future = myStructureTreeModel.invalidateAsync();
    if (onDone != null) {
      future.thenRun(() -> myAsyncTreeModel.onValidThread(onDone));
    }
  }

  public void update(@NotNull TreePath path, boolean structure) {
    myStructureTreeModel.invalidate(path, structure);
  }

  public void update(@NotNull List<? extends TreePath> list, boolean structure) {
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
    TreeVisitor visitor = AbstractProjectViewPane.createVisitor(element, file, list);
    if (visitor != null) acceptAndUpdate(visitor, list, structure);
  }

  private void acceptAndUpdate(@NotNull TreeVisitor visitor, List<? extends TreePath> list, boolean structure) {
    myAsyncTreeModel.accept(visitor, false).onSuccess(path -> update(list, structure));
  }

  private void updatePresentationsFromRootTo(@NotNull VirtualFile file) {
    // find first valid parent for removed file
    while (!file.isValid()) {
      file = file.getParent();
      if (file == null) return;
    }
    SmartList<TreePath> structures = new SmartList<>();
    SmartList<TreePath> presentations = new SmartList<>();
    myAsyncTreeModel.accept(new ProjectViewFileVisitor(file, structures::add) {
      @NotNull
      @Override
      protected Action visit(@NotNull TreePath path, @NotNull AbstractTreeNode node, @NotNull VirtualFile element) {
        Action action = super.visit(path, node, element);
        if (action == Action.CONTINUE) presentations.add(path);
        return action;
      }
    }, false).onSuccess(path -> {
      update(presentations, false);
      update(structures, true);
    });
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

  void setModelTo(@NotNull JTree tree) {
    RestoreSelectionListener listener = new RestoreSelectionListener();
    tree.addTreeSelectionListener(listener);
    tree.setModel(myAsyncTreeModel);
    Disposer.register(myAsyncTreeModel, () -> {
      tree.setModel(null);
      tree.removeTreeSelectionListener(listener);
    });
  }
}