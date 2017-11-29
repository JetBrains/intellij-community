/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.vfs.VirtualFile;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;

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
   * @since 10.0.3
   */
  boolean copy(int line, VirtualFile file, int actionId);

  /**
   * Returns the cursor to show when the drag is over the specified line.
   *
   * @param line the line over which the drag is performed.
   * @param actionId the id of the DnD action {@link java.awt.dnd.DnDConstants}.
   * @return the cursor to show.
   */
  Cursor getCursor(int line, int actionId);

  default void remove() {};

  static DataFlavor[] getFlavors() {return new DataFlavor[] {flavor};};
}
