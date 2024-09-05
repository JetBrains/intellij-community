// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.ide.dnd.DnDEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The attached object of a DnD event, started from the tree in the Services view, implements this interface.
 * See {@link DnDEvent#getAttachedObject()}
 */
public interface ServiceViewDragBeanBase {
  /**
   * @return the list of items dragged in the DnD event.
   */
  @NotNull
  List<Object> getSelectedItems();
}
