// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.bookmark.BookmarksListener;
import com.intellij.ide.bookmark.FileBookmarksListener;
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.projectView.impl.ProjectViewPaneSelectionHelper.SelectionDescriptor;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
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
public final class AsyncProjectViewSupport {
  private static final Logger LOG = Logger.getInstance(AsyncProjectViewSupport.class);
  private final ProjectFileNodeUpdater myNodeUpdater;
  private final StructureTreeModel myStructureTreeModel;
  private final AsyncTreeModel myAsyncTreeModel;
  private boolean myMultiSelectionEnabled = true;

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
    project.getMessageBus().connect(parent).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {

      // structure = true because files may have children too, e.g. if the Show Members option is selected, and children inherit file colors

      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateByFile(file, true);
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateByFile(file, true);
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
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug(
        "AsyncProjectViewSupport.select: " +
        "object=" + object
        + ", file=" + file
      );
    }
    if (object instanceof AbstractTreeNode node) {
      object = node.getValue();
      LOG.debug("select AbstractTreeNode");
      if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
        SelectInProjectViewImplKt.getLOG().debug("Retrieved the value from the node: " + object);
      }
    }
    PsiElement element = object instanceof PsiElement ? (PsiElement)object : null;
    LOG.debug("select object: ", object, " in file: ", file);
    SmartList<TreePath> pathsToSelect = new SmartList<>();
    TreeVisitor visitor = AbstractProjectViewPane.createVisitor(element, file, pathsToSelect);
    if (visitor == null) return ActionCallback.DONE;

    ActionCallback callback = new ActionCallback();
    //noinspection CodeBlock2Expr
    SelectInProjectViewImplKt.getLOG().debug("Updating nodes before selecting");
    myNodeUpdater.updateImmediately(() -> expand(tree, promise -> {
      SelectInProjectViewImplKt.getLOG().debug("Updated nodes");
      promise.onSuccess(o -> callback.setDone());
      if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
        SelectInProjectViewImplKt.getLOG().debug("Collecting paths to select");
      }
      acceptOnEDT(visitor, () -> {
        if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
          SelectInProjectViewImplKt.getLOG().debug("Collected paths to the element: " + pathsToSelect);
        }
        boolean selected = selectPaths(tree, pathsToSelect, visitor);
        if (selected ||
            element == null ||
            file == null ||
            Registry.is("async.project.view.support.extra.select.disabled")) {
          if (selected) {
            SelectInProjectViewImplKt.getLOG().debug("Selected successfully. Done");
          }
          else {
            SelectInProjectViewImplKt.getLOG().debug("Couldn't select, but there's nothing else to do. Done");
          }
          promise.setResult(null);
        }
        else {
          SelectInProjectViewImplKt.getLOG().debug("Couldn't select the element, falling back to selecting the file");
          // try to search the specified file instead of element,
          // because Kotlin files cannot represent containing functions
          pathsToSelect.clear();
          TreeVisitor fileVisitor = AbstractProjectViewPane.createVisitor(null, file, pathsToSelect);
          acceptOnEDT(fileVisitor, () -> {
            if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
              SelectInProjectViewImplKt.getLOG().debug("Collected paths to the file: " + pathsToSelect);
            }
            boolean selectedFile = selectPaths(tree, pathsToSelect, fileVisitor);
            if (selectedFile) {
              SelectInProjectViewImplKt.getLOG().debug("Selected successfully. Done");
            }
            else {
              SelectInProjectViewImplKt.getLOG().debug("Couldn't select, but there's nothing else to do. Done");
            }
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

  public void setMultiSelectionEnabled(boolean enabled) {
    myMultiSelectionEnabled = enabled;
  }

  private boolean selectPaths(@NotNull JTree tree, @NotNull List<TreePath> paths, @NotNull TreeVisitor visitor) {
    if (paths.isEmpty()) {
      SelectInProjectViewImplKt.getLOG().debug("Nothing to select");
      return false;
    }
    if (paths.size() > 1 && myMultiSelectionEnabled) {
      if (visitor instanceof ProjectViewNodeVisitor nodeVisitor) {
        return selectPaths(tree, new SelectionDescriptor(nodeVisitor.getElement(), nodeVisitor.getFile(), paths));
      }
      if (visitor instanceof ProjectViewFileVisitor fileVisitor) {
        return selectPaths(tree, new SelectionDescriptor(null, fileVisitor.getElement(), paths));
      }
    }
    if (!myMultiSelectionEnabled) {
      SelectInProjectViewImplKt.getLOG().debug("Selecting only the first path because multi-selection is disabled");
    }
    TreePath path = paths.get(0);
    tree.expandPath(path); // request to expand found path
    TreeUtil.selectPaths(tree, path); // select and scroll to center
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug("Selected the only path: " + path);
    }
    return true;
  }

  private static boolean selectPaths(@NotNull JTree tree, @NotNull SelectionDescriptor selectionDescriptor) {
    List<? extends TreePath> adjustedPaths = ProjectViewPaneSelectionHelper.getAdjustedPaths(selectionDescriptor);
    adjustedPaths.forEach(it -> tree.expandPath(it));
    TreeUtil.selectPaths(tree, adjustedPaths);
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug("Selected paths adjusted according to " + selectionDescriptor + ": " + adjustedPaths);
    }
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
      @Override
      protected @NotNull Action visit(@NotNull TreePath path, @NotNull AbstractTreeNode node, @NotNull VirtualFile element) {
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
      @Override
      public @NotNull Action visit(@NotNull TreePath path) {
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