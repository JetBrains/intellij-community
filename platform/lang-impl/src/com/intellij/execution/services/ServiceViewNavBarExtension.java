// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.execution.services.ServiceModel.ServiceViewItem;
import com.intellij.icons.AllIcons;
import com.intellij.ide.navbar.impl.DefaultNavBarItem;
import com.intellij.ide.navigationToolbar.AbstractNavBarModelExtension;
import com.intellij.ide.util.treeView.WeighedItem;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ServiceViewNavBarExtension extends AbstractNavBarModelExtension {
  @Nullable
  @Override
  public String getPopupMenuGroup(@NotNull DataProvider provider) {
    ServiceView serviceView = ServiceViewActionProvider.getSelectedView(provider);
    return serviceView == null ? null : ServiceViewActionProvider.SERVICE_VIEW_ITEM_POPUP;
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId, @NotNull DataProvider provider) {
    if (PlatformCoreDataKeys.SELECTED_ITEM.is(dataId)) {
      return unwrapNavBarItem(PlatformCoreDataKeys.SELECTED_ITEM.getData(provider));
    }
    if (PlatformCoreDataKeys.SELECTED_ITEMS.is(dataId)) {
      Object[] items = PlatformCoreDataKeys.SELECTED_ITEMS.getData(provider);
      if (items != null) {
        return ContainerUtil.map2Array(items, ServiceViewNavBarExtension::unwrapNavBarItem);
      }
      return null;
    }
    return null;
  }

  private static Object unwrapNavBarItem(Object item) {
    if (item instanceof DefaultNavBarItem<?> defaultNavBarItem) {
      Object data = defaultNavBarItem.getData();
      if (data instanceof ServiceViewNavBarItem serviceViewNavBarItem) {
        return serviceViewNavBarItem.getItem().getValue();
      }
    }
    return item;
  }

  @Nullable
  @Override
  public String getPresentableText(Object object) {
    if (object instanceof ServiceViewNavBarItem item) {
      return ServiceViewDragHelper.getDisplayName(item.getItem().getViewDescriptor().getPresentation());
    }
    if (object instanceof ServiceViewNavBarRoot) {
      return "";
    }
    return null;
  }

  @Nullable
  @Override
  public Icon getIcon(Object object) {
    if (object instanceof ServiceViewNavBarItem item) {
      return item.getItem().getViewDescriptor().getPresentation().getIcon(false);
    }
    if (object instanceof ServiceViewNavBarRoot) {
      return AllIcons.Nodes.Services;
    }
    return null;
  }

  @Override
  public boolean processChildren(Object object, Object rootElement, Processor<Object> processor) {
    if (object instanceof ServiceViewNavBarRoot root) {
      ServiceViewModel viewModel = root.getViewModel();

      List<? extends ServiceViewItem> roots = viewModel.getVisibleRoots();
      for (int i = 0; i < roots.size(); i++) {
        if (!processor.process(new ServiceViewNavBarItem(roots.get(i), viewModel, -i))) return false;
      }
      return true;
    }
    if (object instanceof ServiceViewNavBarItem navBarItem) {
      ServiceViewModel viewModel = navBarItem.getViewModel();
      ServiceViewItem item = navBarItem.getItem();

      if (item instanceof ServiceModel.ServiceNode service) {
        if (service.getProvidingContributor() != null && !service.isChildrenInitialized()) {
          viewModel.getInvoker().invoke(() -> {
            service.getChildren(); // initialize children on background thread
          });
          return true;
        }
      }
      List<? extends ServiceViewItem> children = viewModel.getChildren(item);
      for (int i = 0; i < children.size(); i++) {
        if (!processor.process(new ServiceViewNavBarItem(children.get(i), viewModel, -i))) return false;
      }
      return true;
    }
    return true;
  }

  static final class ServiceViewNavBarRoot {
    private final ServiceViewModel myViewModel;

    ServiceViewNavBarRoot(@NotNull ServiceViewModel viewModel) {
      myViewModel = viewModel;
    }

    @NotNull ServiceViewModel getViewModel() {
      return myViewModel;
    }
  }

  static final class ServiceViewNavBarItem implements WeighedItem {
    private final ServiceViewItem myItem;
    private final ServiceViewModel myViewModel;
    private final int myWeight;

    ServiceViewNavBarItem(@NotNull ServiceViewItem item, @NotNull ServiceViewModel viewModel, int weight) {
      myItem = item;
      myViewModel = viewModel;
      myWeight = weight;
    }

    @Override
    public int getWeight() {
      return myWeight;
    }

    @NotNull ServiceViewItem getItem() {
      return myItem;
    }

    @NotNull ServiceViewModel getViewModel() {
      return myViewModel;
    }
  }
}
