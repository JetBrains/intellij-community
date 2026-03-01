// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.bookmark.BookmarksListener;
import com.intellij.ide.bookmark.FileBookmarksListener;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.ProblemListener;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.tree.project.ProjectFileNodeUpdater;
import com.intellij.ui.treeStructure.ProjectViewUpdateCause;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.SmartList;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@ApiStatus.Internal
public abstract class ProjectViewPaneSupport {
  private static final Logger LOG = Logger.getInstance(ProjectViewPaneSupport.class);

  private boolean myMultiSelectionEnabled = true;
  protected ProjectFileNodeUpdater myNodeUpdater;

  protected void setupListeners(@NotNull Disposable parent, @NotNull Project project, @NotNull AbstractTreeStructure structure) {
    MessageBusConnection connection = project.getMessageBus().connect(parent);
    connection.subscribe(BookmarksListener.TOPIC, new FileBookmarksListener(file -> {
      updateByFile(file, !file.isDirectory(), List.of(ProjectViewUpdateCause.BOOKMARKS));
    }));
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
      protected void addSubtreeToUpdateByRoot(@NotNull ProjectViewUpdateCause cause) {
        myNodeUpdater.updateFromRoot(cause);
      }

      @Override
      protected boolean addSubtreeToUpdateByElement(@NotNull PsiElement element, @NotNull ProjectViewUpdateCause cause) {
        VirtualFile file = PsiUtilCore.getVirtualFile(element);
        if (file != null) {
          myNodeUpdater.updateFromFile(file, cause);
        }
        else {
          updateByElement(element, true, List.of(cause));
        }
        return true;
      }
    }, parent);
    FileStatusManager.getInstance(project).addFileStatusListener(new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        updateAllPresentations(ProjectViewUpdateCause.FILE_STATUSES);
      }

      @Override
      public void fileStatusChanged(@NotNull VirtualFile file) {
        updateByFile(file, false, List.of(ProjectViewUpdateCause.FILE_STATUS));
      }
    }, parent);
    CopyPasteUtil.addDefaultListener(parent, element -> {
      updateByElement(element, false, List.of(ProjectViewUpdateCause.CLIPBOARD));
    });
    project.getMessageBus().connect(parent).subscribe(ProblemListener.TOPIC, new ProblemListener() {
      @Override
      public void problemsAppeared(@NotNull VirtualFile file) {
        updatePresentationsFromRootTo(file, ProjectViewUpdateCause.PROBLEMS_APPEARED);
      }

      @Override
      public void problemsDisappeared(@NotNull VirtualFile file) {
        updatePresentationsFromRootTo(file, ProjectViewUpdateCause.PROBLEMS_DISAPPEARED);
      }
    });
    project.getMessageBus().connect(parent).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {

      // structure = true because files may have children too, e.g. if the Show Members option is selected, and children inherit file colors

      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateByFile(file, true, List.of(ProjectViewUpdateCause.FILE_OPENED));
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateByFile(file, true, List.of(ProjectViewUpdateCause.FILE_CLOSED));
      }
    });
  }

  public abstract void setModelTo(@NotNull JTree tree);

  public abstract void setComparator(@Nullable Comparator<? super NodeDescriptor<?>> comparator);

  public void setMultiSelectionEnabled(boolean enabled) {
    myMultiSelectionEnabled = enabled;
  }

  public abstract void updateAll(@Nullable Runnable onDone, @NotNull Collection<ProjectViewUpdateCause> causes);

  public void update(@NotNull List<? extends TreePath> list, boolean structure, @NotNull Collection<ProjectViewUpdateCause> causes) {
    for (TreePath path : list) update(path, structure, causes);
  }

  public abstract void update(@NotNull TreePath path, boolean structure, @NotNull Collection<ProjectViewUpdateCause> causes);

  public void updateByFile(@NotNull VirtualFile file, boolean structure, @NotNull Collection<ProjectViewUpdateCause> causes) {
    LOG.debug(structure ? "updateChildrenByFile: " : "updatePresentationByFile: ", file);
    update(null, file, structure, causes);
  }

  public void updateByElement(@NotNull PsiElement element, boolean structure, @NotNull Collection<ProjectViewUpdateCause> causes) {
    LOG.debug(structure ? "updateChildrenByElement: " : "updatePresentationByElement: ", element);
    update(element, null, structure, causes);
  }

  protected void update(@Nullable PsiElement element, @Nullable VirtualFile file, boolean structure, @NotNull Collection<ProjectViewUpdateCause> causes) {
    SmartList<TreePath> list = new SmartList<>();
    TreeVisitor visitor = AbstractProjectViewPane.createVisitor(element, file, list);
    if (visitor != null) acceptAndUpdate(visitor, structure ? null : list, structure ? list : null, causes);
  }

  @SuppressWarnings("SameParameterValue") // really, one value now, maybe we'll need more later
  private void updateAllPresentations(@NotNull ProjectViewUpdateCause cause) {
    SmartList<TreePath> list = new SmartList<>();
    acceptAndUpdate(new TreeVisitor() {
      @Override
      public @NotNull Action visit(@NotNull TreePath path) {
        list.add(path);
        return Action.CONTINUE;
      }
    }, list, null, List.of(cause));
  }

  protected void updatePresentationsFromRootTo(@NotNull VirtualFile file, @NotNull ProjectViewUpdateCause cause) {
    // find first valid parent for removed file
    while (!file.isValid()) {
      file = file.getParent();
      if (file == null) return;
    }
    SmartList<TreePath> structures = new SmartList<>();
    SmartList<TreePath> presentations = new SmartList<>();
    var visitor = new ProjectViewFileVisitor(file, structures::add) {
      @Override
      protected @NotNull Action visit(@NotNull TreePath path, @NotNull AbstractTreeNode node, @NotNull VirtualFile element) {
        Action action = super.visit(path, node, element);
        if (action == Action.CONTINUE) presentations.add(path);
        return action;
      }
    };
    acceptAndUpdate(visitor, presentations, structures, List.of(cause));
  }

  protected abstract void acceptAndUpdate(
    @NotNull TreeVisitor visitor,
    @Nullable List<? extends TreePath> presentations,
    @Nullable List<? extends TreePath> structures,
    @NotNull Collection<ProjectViewUpdateCause> causes
  );

  public abstract @NotNull ActionCallback select(@NotNull JTree tree, @Nullable Object object, @Nullable VirtualFile file);

  protected boolean isMultiSelectionEnabled() {
    return myMultiSelectionEnabled;
  }

  protected boolean selectPaths(@NotNull JTree tree, @NotNull List<TreePath> paths, @NotNull TreeVisitor visitor) {
    if (paths.isEmpty()) {
      LOG.debug("Nothing to select");
      return false;
    }
    if (paths.size() > 1 && myMultiSelectionEnabled) {
      if (visitor instanceof ProjectViewNodeVisitor nodeVisitor) {
        return selectPaths(tree, new ProjectViewPaneSelectionHelper.SelectionDescriptor(nodeVisitor.getElement(), nodeVisitor.getFile(), paths));
      }
      if (visitor instanceof ProjectViewFileVisitor fileVisitor) {
        return selectPaths(tree, new ProjectViewPaneSelectionHelper.SelectionDescriptor(null, fileVisitor.getElement(), paths));
      }
    }
    if (!myMultiSelectionEnabled) {
      LOG.debug("Selecting only the first path because multi-selection is disabled");
    }
    TreePath path = paths.get(0);
    tree.expandPath(path); // request to expand found path
    TreeUtil.selectPaths(tree, path); // select and scroll to center
    if (LOG.isDebugEnabled()) {
      LOG.debug("Selected the only path: " + path);
    }
    if (tree instanceof Tree) {
      ((Tree)tree).onPathSelected(path);
    }
    return true;
  }

  protected static boolean selectPaths(@NotNull JTree tree, @NotNull ProjectViewPaneSelectionHelper.SelectionDescriptor selectionDescriptor) {
    List<? extends TreePath> adjustedPaths = ProjectViewPaneSelectionHelper.getAdjustedPaths(selectionDescriptor);
    adjustedPaths.forEach(it -> tree.expandPath(it));
    TreeUtil.selectPaths(tree, adjustedPaths);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Selected paths adjusted according to " + selectionDescriptor + ": " + adjustedPaths);
    }
    if (!adjustedPaths.isEmpty() && tree instanceof Tree) {
      ((Tree)tree).onPathSelected(adjustedPaths.getFirst());
    }
    return true;
  }
}
