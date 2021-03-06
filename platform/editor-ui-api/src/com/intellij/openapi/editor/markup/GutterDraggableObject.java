// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.vfs.VirtualFile;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DragSource;

/**
 * Interface which should be implemented to handle drag and drop of gutter icons. An example of
 * a gutter icon which can be dragged and dropped is the breakpoint icon.
 *
 * @author ven
 * @author Konstantin Bulenkov
 * @see GutterIconRenderer#getDraggableObject()
 */
public interface GutterDraggableObject {

  DataFlavor flavor = new DataFlavor(GutterDraggableObject.class, "Gutter Draggable Object");

  /**
   * Called when the icon is dropped over the specified line.
   *
   *
   * @param line the line over which the icon has been dropped.
   * @param file the DnD target file
   * @param actionId the id of the DnD action {@link java.awt.dnd.DnDConstants}.
   * @return true if the drag and drop operation has completed successfully, false otherwise.
   */
  boolean copy(int line, VirtualFile file, int actionId);

  /**
   * Returns the cursor to show when the drag is over the specified line.
   *
   * @param line the line over which the drag is performed.
   * @param actionId the id of the DnD action {@link java.awt.dnd.DnDConstants}.
   * @return the cursor to show.
   * @deprecated override {@link #getCursor(int, VirtualFile, int)}
   */
  @Deprecated
  default Cursor getCursor(int line, int actionId) {
    return DragSource.DefaultMoveDrop;
  }

  /**
   * Returns the cursor to show when the drag is over the specified line.
   *
   * @param line the line over which the drag is performed.
   * @param file the DnD target file
   * @param actionId the id of the DnD action {@link java.awt.dnd.DnDConstants}.
   * @return the cursor to show.
   */
  default Cursor getCursor(int line, VirtualFile file, int actionId) {
    return getCursor(line, actionId);
  }

  default void remove() {}

  static DataFlavor[] getFlavors() {return new DataFlavor[] {flavor};}
}
