// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.tree.*;
import com.intellij.ui.tree.project.ProjectFileNode;
import com.intellij.ui.tree.project.ProjectFileNodeUpdater;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public final class AsyncProjectViewSupport extends ProjectViewPaneSupport {
  private static final Logger LOG = Logger.getInstance(AsyncProjectViewSupport.class);
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
            if (file != null && ProjectFileNode.findArea(file, project) != null) collector.add(file);
          }
          List<VirtualFile> roots = collector.get();
          LOG.debug("found ", roots.size(), " roots in ", System.currentTimeMillis() - time, "ms");
          roots.forEach(root -> updateByFile(root, true));
        }
      }
    };
    setupListeners(parent, project, structure);
  }

  public AsyncTreeModel getTreeModel() {
    return myAsyncTreeModel;
  }

  @Override
  public void setComparator(@Nullable Comparator<? super NodeDescriptor<?>> comparator) {
    myStructureTreeModel.setComparator(comparator);
  }

  @Override
  public @NotNull ActionCallback select(@NotNull JTree tree, @Nullable Object object, @Nullable VirtualFile file) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "AsyncProjectViewSupport.select: " +
        "object=" + object
        + ", file=" + file
      );
    }
    if (object instanceof AbstractTreeNode node) {
      object = node.getValue();
      LOG.debug("select AbstractTreeNode");
      if (LOG.isDebugEnabled()) {
        LOG.debug("Retrieved the value from the node: " + object);
      }
    }
    PsiElement element = object instanceof PsiElement ? (PsiElement)object : null;
    LOG.debug("select object: ", object, " in file: ", file);
    SmartList<TreePath> pathsToSelect = new SmartList<>();
    TreeVisitor visitor = AbstractProjectViewPane.createVisitor(element, file, pathsToSelect);
    if (visitor == null) return ActionCallback.DONE;

    ActionCallback callback = new ActionCallback();
    //noinspection CodeBlock2Expr
    LOG.debug("Updating nodes before selecting");
    myNodeUpdater.updateImmediately(() -> TreeState.expand(tree, promise -> {
      LOG.debug("Updated nodes");
      promise.onSuccess(o -> callback.setDone());
      if (LOG.isDebugEnabled()) {
        LOG.debug("Collecting paths to select");
      }
      acceptOnEDT(visitor, () -> {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Collected paths to the element: " + pathsToSelect);
        }
        boolean selected = selectPaths(tree, pathsToSelect, visitor);
        if (selected ||
            element == null ||
            file == null ||
            Registry.is("async.project.view.support.extra.select.disabled")) {
          if (selected) {
            LOG.debug("Selected successfully. Done");
          }
          else {
            LOG.debug("Couldn't select, but there's nothing else to do. Done");
          }
          promise.setResult(null);
        }
        else {
          LOG.debug("Couldn't select the element, falling back to selecting the file");
          // try to search the specified file instead of element,
          // because Kotlin files cannot represent containing functions
          pathsToSelect.clear();
          TreeVisitor fileVisitor = AbstractProjectViewPane.createVisitor(null, file, pathsToSelect);
          acceptOnEDT(fileVisitor, () -> {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Collected paths to the file: " + pathsToSelect);
            }
            boolean selectedFile = selectPaths(tree, pathsToSelect, fileVisitor);
            if (selectedFile) {
              LOG.debug("Selected successfully. Done");
            }
            else {
              LOG.debug("Couldn't select, but there's nothing else to do. Done");
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

  @Override
  public void updateAll(Runnable onDone) {
    LOG.debug(new RuntimeException("reload a whole tree"));
    CompletableFuture<?> future = myStructureTreeModel.invalidateAsync();
    if (onDone != null) {
      future.thenRun(() -> myAsyncTreeModel.onValidThread(onDone));
    }
  }

  @Override
  public void update(@NotNull TreePath path, boolean structure) {
    myStructureTreeModel.invalidate(path, structure);
  }

  @Override
  protected void acceptAndUpdate(
    @NotNull TreeVisitor visitor,
    @Nullable List<? extends TreePath> presentations,
    @Nullable List<? extends TreePath> structures
  ) {
    myAsyncTreeModel.accept(visitor, false).onSuccess(path -> {
      if (presentations != null) update(presentations, false);
      if (structures != null) update(structures, true);
    });
  }

  @Override
  public void setModelTo(@NotNull JTree tree) {
    RestoreSelectionListener listener = new RestoreSelectionListener();
    tree.addTreeSelectionListener(listener);
    tree.setModel(myAsyncTreeModel);
    Disposer.register(myAsyncTreeModel, () -> {
      tree.setModel(null);
      tree.removeTreeSelectionListener(listener);
    });
  }
}