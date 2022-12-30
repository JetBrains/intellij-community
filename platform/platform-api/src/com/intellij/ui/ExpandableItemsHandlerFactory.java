// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public abstract class ExpandableItemsHandlerFactory {
  @SuppressWarnings("unchecked")
  public static <T> @NotNull ExpandableItemsHandler<T> install(@NotNull JComponent component) {
    ExpandableItemsHandlerFactory factory = getInstance();
    ExpandableItemsHandler<?> handler = factory == null ? null : factory.doInstall(component);
    return (ExpandableItemsHandler<T>)(handler == null ? NULL : handler);
  }

  @Nullable
  private static ExpandableItemsHandlerFactory getInstance() {
    if (!LoadingState.COMPONENTS_REGISTERED.isOccurred() || !Registry.is("ide.windowSystem.showListItemsPopup", true)) {
      return null;
    }
    return ApplicationManager.getApplication().getService(ExpandableItemsHandlerFactory.class);
  }

  protected abstract @Nullable ExpandableItemsHandler<?> doInstall(@NotNull JComponent component);

  private static final ExpandableItemsHandler<?> NULL = new ExpandableItemsHandler<>() {
    @Override
    public void setEnabled(boolean enabled) {
    }

    @Override
    public boolean isEnabled() {
      return false;
    }

    @NotNull
    @Override
    public Collection<Object> getExpandedItems() {
      return Collections.emptyList();
    }
  };
}