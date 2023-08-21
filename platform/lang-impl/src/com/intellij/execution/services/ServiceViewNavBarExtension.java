// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.ide.navbar.impl.DefaultNavBarItem;
import com.intellij.ide.navigationToolbar.AbstractNavBarModelExtension;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ServiceViewNavBarExtension extends AbstractNavBarModelExtension {
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
      if (data instanceof ServiceModel.ServiceViewItem serviceViewItem) {
        return serviceViewItem.getValue();
      }
    }
    return item;
  }

  @Override
  public @Nullable String getPresentableText(Object object) {
    return null;
  }
}
