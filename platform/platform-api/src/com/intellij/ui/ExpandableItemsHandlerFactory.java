// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public abstract class ExpandableItemsHandlerFactory {
  public static ExpandableItemsHandler<Integer> install(JList list) {
    ExpandableItemsHandlerFactory i = getInstance();
    return i == null ? (ExpandableItemsHandler<Integer>)NULL : i.doInstall(list);
  }

  public static ExpandableItemsHandler<Integer> install(JTree tree) {
    ExpandableItemsHandlerFactory i = getInstance();
    return i == null ? (ExpandableItemsHandler<Integer>)NULL : i.doInstall(tree);
  }

  public static ExpandableItemsHandler<TableCell> install(JTable table) {
    ExpandableItemsHandlerFactory i = getInstance();
    return i == null ? (ExpandableItemsHandler<TableCell>)NULL : i.doInstall(table);
  }

  @Nullable
  private static ExpandableItemsHandlerFactory getInstance() {
    if (!Registry.is("ide.windowSystem.showListItemsPopup") || ApplicationManager.getApplication() == null) return null;
    return ApplicationManager.getApplication().getService(ExpandableItemsHandlerFactory.class);
  }

  protected abstract ExpandableItemsHandler<Integer> doInstall(JList list);

  protected abstract ExpandableItemsHandler<Integer> doInstall(JTree tree);

  protected abstract ExpandableItemsHandler<TableCell> doInstall(JTable table);

  private static final ExpandableItemsHandler NULL = new ExpandableItemsHandler<Object>() {
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