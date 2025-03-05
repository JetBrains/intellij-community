// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.ide.navigationToolbar.AbstractNavBarModelExtension;
import com.intellij.openapi.actionSystem.DataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ServiceViewNavBarExtension extends AbstractNavBarModelExtension {
  @Override
  public @Nullable String getPopupMenuGroup(@NotNull DataProvider provider) {
    ServiceView serviceView = ServiceViewActionProvider.getSelectedView(provider);
    return serviceView == null ? null : ServiceViewActionProvider.SERVICE_VIEW_ITEM_POPUP;
  }

  @Override
  public @Nullable String getPresentableText(Object object) {
    return null;
  }
}
