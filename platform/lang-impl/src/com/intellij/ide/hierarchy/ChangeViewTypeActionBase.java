// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.hierarchy;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

@ApiStatus.Internal
public abstract class ChangeViewTypeActionBase extends ChangeHierarchyViewActionBase {
  ChangeViewTypeActionBase(@NotNull Supplier<String> shortDescription, @NotNull Supplier<String> longDescription, Icon icon) {
    super(shortDescription, longDescription, icon);
  }

  @Override
  protected @Nullable TypeHierarchyBrowserBase getHierarchyBrowser(@NotNull DataContext dataContext) {
    return ObjectUtils.tryCast(super.getHierarchyBrowser(dataContext), TypeHierarchyBrowserBase.class);
  }
}
