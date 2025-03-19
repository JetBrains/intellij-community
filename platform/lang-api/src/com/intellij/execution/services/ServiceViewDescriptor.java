// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.ide.DataManager;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Key;
import com.intellij.pom.Navigatable;
import com.intellij.util.OpenSourceUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.List;

/// Implementation of this interface represents a node in the Service View.
/// It provides information on how the node will be represented in the Service View Tree (by the [#getPresentation()]) method,
/// how it should be shown in the detail panel (be the [#getContentComponent()])
///  and available actions ([#getPopupActions()] and [#getToolbarActions()])
///
/// Subclasses could also implement the [UiDataProvider] to provide the [DataContext]
/// for the actions returned by [#getToolbarActions()] and [#getPopupActions()]
///
/// @see ServiceViewContributor
public interface ServiceViewDescriptor {
  Key<Boolean> ACTION_HOLDER_KEY = Key.create("ServiceViewActionHolderContentComponent");

  @NotNull
  ItemPresentation getPresentation();

  default @Nullable String getId() {
    return getPresentation().getPresentableText();
  }

  default @Nullable JComponent getContentComponent() {
    return null;
  }

  default boolean isContentPartVisible() {
    return true;
  }

  default @NotNull ItemPresentation getContentPresentation() {
    return getPresentation();
  }

  default @NotNull ItemPresentation getCustomPresentation(@NotNull ServiceViewOptions options, @NotNull ServiceViewItemState state) {
    return getPresentation();
  }

  default @Nullable ActionGroup getToolbarActions() {
    return null;
  }

  default @Nullable ActionGroup getPopupActions() {
    return getToolbarActions();
  }

  /// Obsolete, implement [UiDataProvider] instead
  @ApiStatus.Obsolete
  default @Nullable DataProvider getDataProvider() {
    return null;
  }

  default void onNodeSelected(List<Object> selectedServices) {
  }

  default void onNodeUnselected() {
  }

  default boolean handleDoubleClick(@NotNull MouseEvent event) {
    Navigatable navigatable = getNavigatable();
    if (navigatable == null) return false;

    DataContext dataContext = DataManager.getInstance().getDataContext(event.getComponent());
    DataContext wrapper = CustomizedDataContext.withSnapshot(dataContext, sink -> {
      sink.set(CommonDataKeys.NAVIGATABLE, navigatable);
    });
    OpenSourceUtil.openSourcesFrom(wrapper, false);
    return true;
  }

  default @Nullable Object getPresentationTag(Object fragment) {
    return null;
  }

  default @Nullable Navigatable getNavigatable() {
    return null;
  }

  default @Nullable Runnable getRemover() {
    return null;
  }

  default boolean isVisible() {
    return true;
  }
}
