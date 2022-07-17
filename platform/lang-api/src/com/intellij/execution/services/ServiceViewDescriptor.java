// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

public interface ServiceViewDescriptor {
  @NotNull
  ItemPresentation getPresentation();

  @Nullable
  default String getId() {
    return getPresentation().getPresentableText();
  }

  @Nullable
  default JComponent getContentComponent() {
    return null;
  }

  @NotNull
  default ItemPresentation getContentPresentation() {
    return getPresentation();
  }

  @NotNull
  default ItemPresentation getCustomPresentation(@NotNull ServiceViewOptions options) {
    return getPresentation();
  }

  @Nullable
  default ActionGroup getToolbarActions() {
    return null;
  }

  @Nullable
  default ActionGroup getPopupActions() {
    return getToolbarActions();
  }

  @Nullable
  default DataProvider getDataProvider() {
    return null;
  }

  default void onNodeSelected() {
  }

  default void onNodeUnselected() {
  }

  default boolean handleDoubleClick(@NotNull MouseEvent event) {
    Navigatable navigatable = getNavigatable();
    if (navigatable != null && navigatable.canNavigateToSource()) {
      navigatable.navigate(true);
      return true;
    }
    return false;
  }

  @Nullable
  default Object getPresentationTag(Object fragment) {
    return null;
  }

  @Nullable
  default Navigatable getNavigatable() {
    return null;
  }

  @Nullable
  default Runnable getRemover() {
    return null;
  }

  default boolean isVisible() {
    return true;
  }
}
