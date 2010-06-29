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

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public abstract class ExpandTipHandlerFactory {
  public static ExpandTipHandler<Integer> install(JList list) {
    ExpandTipHandlerFactory i = getInstance();
    return i == null ? (ExpandTipHandler<Integer>)NULL : i.doInstall(list);
  }

  public static ExpandTipHandler<Integer> install(JTree tree) {
    ExpandTipHandlerFactory i = getInstance();
    return i == null ? (ExpandTipHandler<Integer>)NULL : i.doInstall(tree);
  }

  public static ExpandTipHandler<TableCell> install(JTable table) {
    ExpandTipHandlerFactory i = getInstance();
    return i == null ? (ExpandTipHandler<TableCell>)NULL : i.doInstall(table);
  }

  private static ExpandTipHandlerFactory getInstance() {
    return ServiceManager.getService(ExpandTipHandlerFactory.class);
  }

  protected abstract ExpandTipHandler<Integer> doInstall(JList list);

  protected abstract ExpandTipHandler<Integer> doInstall(JTree tree);

  protected abstract ExpandTipHandler<TableCell> doInstall(JTable table);

  private static final ExpandTipHandler NULL = new ExpandTipHandler<Object>() {
    @NotNull
    @Override
    public Collection<Object> getExpandedItems() {
      return Collections.emptyList();
    }
  };
}