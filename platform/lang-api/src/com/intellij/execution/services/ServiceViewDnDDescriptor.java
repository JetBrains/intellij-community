// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.ide.dnd.DnDEvent;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.util.List;

/**
 * {@link ServiceViewDescriptor} should implement this interface in order to accept and process DnD events.
 */
public interface ServiceViewDnDDescriptor {

  /**
   * Determines if a drop operation can be performed for the given DnD event at the specified position.
   *
   * @param event The DnDEvent associated with the drop.
   * @param position The position where the drop occurs.
   * @return {@code true} if drop operation can be performed, otherwise {@code false}
   */
  boolean canDrop(@NotNull DnDEvent event, @NotNull Position position);

  /**
   * Handles the drop action in a drag-and-drop operation.
   *
   * @param event The DnDEvent associated with the drop.
   * @param position The position where the drop occurs.
   */
  void drop(@NotNull DnDEvent event, @NotNull Position position);

  enum Position {
    ABOVE, INTO, BELOW
  }

  /**
   * Represents the {@link DataFlavor} for transferring lists of service view items during drag-and-drop operations.
   */
  DataFlavor LIST_DATA_FLAVOR = new DataFlavor(List.class, "Service View items");
}
