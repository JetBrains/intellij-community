/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
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
    return ServiceManager.getService(ExpandableItemsHandlerFactory.class);
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