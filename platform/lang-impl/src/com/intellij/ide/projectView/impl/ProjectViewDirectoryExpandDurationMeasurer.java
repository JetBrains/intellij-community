// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ui.LoadingNode;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.concurrent.atomic.AtomicBoolean;

final class ProjectViewDirectoryExpandDurationMeasurer {

  private final TreeModel model;
  private final @NotNull TreePath path;
  private final @NotNull Runnable onDetach;
  private final AtomicBoolean detached = new AtomicBoolean();
  private long start;
  private @Nullable TreeModelListener modelListener;

  ProjectViewDirectoryExpandDurationMeasurer(@NotNull TreeModel model, @NotNull TreePath path, @NotNull Runnable onDetach) {
    this.model = model;
    this.path = path;
    this.onDetach = onDetach;
  }

  void start() {
    start = System.currentTimeMillis();
  }

  void checkExpanded(@Nullable TreePath changedPath) {
    if (!this.path.equals(changedPath)) {
      return;
    }
    checkExpanded();
  }

  private void checkExpanded() {
    var childCount = model.getChildCount(path.getLastPathComponent());
    if (childCount > 1) { // Definitely more than just the 'loading...' node.
      finishMeasuring();
    }
    else if (childCount == 1) {
      var child = model.getChild(path.getLastPathComponent(), 0);
      if (child instanceof LoadingNode) {
        ensureSubscribedToModel(); // Need to wait for async load.
      }
      else {
        finishMeasuring();
      }
    }
  }

  private void ensureSubscribedToModel() {
    if (modelListener != null) {
      return;
    }
    var modelListener = TreeModelAdapter.create(this::processModelEvent);
    model.addTreeModelListener(modelListener);
    this.modelListener = modelListener;
  }

  private void processModelEvent(TreeModelEvent event, TreeModelAdapter.EventType type) {
    var path = event.getTreePath();
    if (path == null) {
      // Shouldn't normally happen for regular events, may be a global change (the entire model is cleared).
      detach();
      return;
    }
    switch (type) {
      case StructureChanged -> {
        if (path.equals(this.path)) {
          checkExpanded();
        }
        else if (path.isDescendant(this.path)) {
          detach(); // One of the parent dirs was rebuilt completely.
        }
      }
      case NodesChanged, NodesInserted -> {
        checkExpanded(path);
      }
      case NodesRemoved -> {
        if (path.isDescendant(this.path.getParentPath())) {
          detach(); // Our directory was removed.
        }
        else {
          checkExpanded(path); // Could be a result of an empty dir expansion! ('loading...' removed)
        }
      }
    }
  }

  private void finishMeasuring() {
    // We now know that the model is updated, but the tree may be still in the process of updating,
    // so we only finish measuring when the tree is done and the EDT is free again.
    SwingUtilities.invokeLater(() -> {
      var finish = System.currentTimeMillis();
      ProjectViewPerformanceCollector.logExpandDirDuration(finish - start);
    });
    // Detach right now to avoid sending multiple values.
    detach();
  }

  void detach() {
    if (!detached.compareAndSet(false, true)) {
      return;
    }
    ensureUnsubscribedFromModel();
    onDetach.run();
  }

  private void ensureUnsubscribedFromModel() {
    var modelListener = this.modelListener;
    if (modelListener == null) {
      return;
    }
    model.removeTreeModelListener(modelListener);
    this.modelListener = null;
  }
}
