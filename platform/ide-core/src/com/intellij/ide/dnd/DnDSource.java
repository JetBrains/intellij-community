// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public interface DnDSource extends DnDDropActionHandler {
  boolean canStartDragging(DnDAction action, @NotNull Point dragOrigin);

  DnDDragStartBean startDragging(DnDAction action, @NotNull Point dragOrigin);

  /**
   * Image to be drawn on screen while dragging and the point of the offset to position cursor
   * in the proper place
   *
   * @param action drag-n-drop action
   * @param dragOrigin origin drag point
   * @param bean a bean to create an image for
   * @return Pair of image and cursor offset at the image
   */
  @Nullable
  default Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin, @NotNull DnDDragStartBean bean) {
    return createDraggedImage(action, dragOrigin);
  }

  /**
   * @deprecated override {@link DnDSource#createDraggedImage(DnDAction, Point, DnDDragStartBean)} instead
   */
  @Deprecated(forRemoval = true)
  @Nullable
  default Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
    return null;
  }

  default void dragDropEnd() {
  }

  @Override
  default void dropActionChanged(int gestureModifiers) {
  }
}
