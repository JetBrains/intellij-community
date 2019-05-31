// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.execution.services.ServiceModel.ServiceViewItem;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.Queue;
import java.util.*;

class ServiceTreeView extends ServiceView {
  private final ServiceViewTree myTree;
  private final ServiceViewTreeModel myTreeModel;

  private volatile ServiceViewItem myLastSelection;
  private boolean mySelected;

  ServiceTreeView(@NotNull Project project, @NotNull ServiceViewModel model, @NotNull ServiceViewUi ui, @NotNull ServiceViewState state) {
    super(new BorderLayout(), project, model, ui);

    myTreeModel = new ServiceViewTreeModel(model);
    myTree = new ServiceViewTree(myTreeModel, this);
    myTree.setShowsRootHandles(!model.isFlat());

    ServiceViewActionProvider actionProvider = ServiceViewActionProvider.getInstance();
    ui.setServiceToolbar(actionProvider);
    ui.setMasterPanel(myTree, actionProvider);
    DnDManager.getInstance().registerSource(ServiceViewDragHelper.createSource(this), myTree);
    add(myUi.getComponent(), BorderLayout.CENTER);

    myTree.addTreeSelectionListener(e -> onSelectionChanged());
    model.addModelListener(this::rootsChanged);

    state.treeState.applyTo(myTree, myTreeModel.getRoot());
  }

  @Override
  void saveState(@NotNull ServiceViewState state) {
    super.saveState(state);
    myUi.saveState(state);
    state.treeState = TreeState.createOn(myTree);
  }

  @NotNull
  @Override
  List<ServiceViewItem> getSelectedItems() {
    int[] rows = myTree.getSelectionRows();
    if (rows == null || rows.length == 0) return Collections.emptyList();

    List<ServiceViewItem> result = new ArrayList<>();
    Arrays.sort(rows);
    for (int row : rows) {
      TreePath path = myTree.getPathForRow(row);
      ServiceViewItem item = path == null ? null : ObjectUtils.tryCast(path.getLastPathComponent(), ServiceViewItem.class);
      if (item != null) {
        result.add(item);
      }
    }
    return result;
  }

  @Override
  Promise<Void> select(@NotNull Object service, @NotNull Class<?> contributorClass) {
    if (myLastSelection == null || !myLastSelection.getValue().equals(service)) {
      AsyncPromise<Void> result = new AsyncPromise<>();
      myTreeModel.findPath(service, contributorClass)
        .onError(result::setError)
        .onSuccess(path -> TreeUtil.promiseSelect(myTree, new PathSelectionVisitor(path))
          .onError(result::setError)
          .onSuccess(selectedPath -> result.setResult(null)));
      return result;
    }
    return Promises.resolvedPromise();
  }

  @Override
  void onViewSelected() {
    mySelected = true;
    if (myLastSelection != null) {
      ServiceViewDescriptor descriptor = myLastSelection.getViewDescriptor();
      onNodeSelected(descriptor);
      myUi.setDetailsComponent(descriptor.getContentComponent());
    }
  }

  @Override
  void onViewUnselected() {
    mySelected = false;
    if (myLastSelection != null) {
      myLastSelection.getViewDescriptor().onNodeUnselected();
    }
  }

  @Override
  void setFlat(boolean flat) {
    super.setFlat(flat);
    myTree.setShowsRootHandles(!flat);
  }

  private void onSelectionChanged() {
    List<ServiceViewItem> selected = getSelectedItems();
    ServiceViewItem newSelection = ContainerUtil.getOnlyItem(selected);
    if (Comparing.equal(newSelection, myLastSelection)) return;

    ServiceViewDescriptor oldDescriptor = myLastSelection == null ? null : myLastSelection.getViewDescriptor();
    if (oldDescriptor != null && mySelected) {
      oldDescriptor.onNodeUnselected();
    }

    myLastSelection = newSelection;
    ServiceViewDescriptor newDescriptor = newSelection == null ? null : newSelection.getViewDescriptor();

    if (newDescriptor != null) {
      onNodeSelected(newDescriptor);
    }

    myUi.setDetailsComponent(newDescriptor == null ? null : newDescriptor.getContentComponent());
  }

  private void rootsChanged() {
    ServiceViewItem lastSelection = myLastSelection;
    if (lastSelection == null) return;

    ServiceViewItem updatedItem = getModel().findItem(lastSelection);
    myLastSelection = updatedItem;

    AppUIUtil.invokeOnEdt(() -> {
      if (mySelected && updatedItem == myLastSelection) {
        ServiceViewDescriptor descriptor = updatedItem == null ? null : updatedItem.getViewDescriptor();
        myUi.setDetailsComponent(descriptor == null ? null : descriptor.getContentComponent());
      }
    }, myProject.getDisposed());
  }

  @Override
  List<Object> getChildrenSafe(@NotNull Object value) {
    int count = myTree.getRowCount();
    for (int i = 0; i < count; i++) {
      TreePath path = myTree.getPathForRow(i);
      Object node = path.getLastPathComponent();
      if (!(node instanceof ServiceViewItem)) continue;

      ServiceViewItem item = (ServiceViewItem)node;
      if (!value.equals(item.getValue())) continue;

      return ContainerUtil.map(getModel().getChildren(item), ServiceViewItem::getValue);
    }
    return Collections.emptyList();
  }

  private static class PathSelectionVisitor implements TreeVisitor {
    private final Queue<Object> myPath;

    PathSelectionVisitor(TreePath path) {
      myPath = ContainerUtil.newLinkedList(path.getPath());
    }

    @NotNull
    @Override
    public Action visit(@NotNull TreePath path) {
      Object node = path.getLastPathComponent();
      if (node.equals(myPath.peek())) {
        myPath.poll();
        return myPath.isEmpty() ? Action.INTERRUPT : Action.CONTINUE;
      }
      return Action.SKIP_CHILDREN;
    }
  }
}
