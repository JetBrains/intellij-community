// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.platform.execution.serviceView.ServiceModel.ServiceViewItem;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ServiceViewTreeModel extends BaseTreeModel<Object> implements InvokerSupplier {
  private final ServiceViewModel myModel;
  private final Object myRoot = ObjectUtils.sentinel("services root");

  ServiceViewTreeModel(@NotNull ServiceViewModel model) {
    myModel = model;
  }

  @Override
  public @NotNull Invoker getInvoker() {
    return myModel.getInvoker();
  }

  @Override
  public void dispose() {
  }

  @Override
  public boolean isLeaf(Object object) {
    if (object == myRoot) return false;

    if (object instanceof ServiceModel.ServiceNode node) {
      if (!node.isChildrenInitialized() && !node.isLoaded()) {
        return false;
      }
    }
    return myModel.getChildren(((ServiceViewItem)object)).isEmpty();
  }

  @Override
  public List<?> getChildren(Object parent) {
    if (parent == myRoot) {
      return myModel.getVisibleRoots();
    }
    return myModel.getChildren(((ServiceViewItem)parent));
  }

  @Override
  public Object getRoot() {
    return myRoot;
  }

  void rootsChanged() {
    treeStructureChanged(new TreePath(myRoot), null, null);
  }

  Promise<TreePath> findPath(@NotNull Object service, @NotNull Class<?> contributorClass) {
    return doFindPath(service, contributorClass, false);
  }

  Promise<TreePath> findPathSafe(@NotNull Object service, @NotNull Class<?> contributorClass) {
    return doFindPath(service, contributorClass, true);
  }

  private Promise<TreePath> doFindPath(@NotNull Object service, @NotNull Class<?> contributorClass, boolean safe) {
    AsyncPromise<TreePath> result = new AsyncPromise<>();
    getInvoker().invoke(() -> {
      List<? extends ServiceViewItem> roots = myModel.getVisibleRoots();
      ServiceViewItem serviceNode = JBTreeTraverser.from((Function<ServiceViewItem, List<ServiceViewItem>>)node ->
        contributorClass.isInstance(node.getRootContributor()) ? new ArrayList<>(myModel.getChildren(node)) : null)
        .withRoots(roots)
        .traverse(safe ? ServiceModel.ONLY_LOADED_BFS : ServiceModel.NOT_LOADED_LAST_BFS)
        .filter(node -> node.getValue().equals(service))
        .first();
      if (serviceNode != null) {
        List<Object> path = new ArrayList<>();
        do {
          path.add(serviceNode);
          serviceNode = roots.contains(serviceNode) ? null : serviceNode.getParent();
        }
        while (serviceNode != null);
        path.add(myRoot);
        Collections.reverse(path);
        result.setResult(new TreePath(ArrayUtil.toObjectArray(path)));
        return;
      }

      result.setError("Service not found");
    });
    return result;
  }
}
