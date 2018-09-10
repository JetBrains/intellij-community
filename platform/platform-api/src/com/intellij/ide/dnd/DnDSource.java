/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.dnd;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public interface DnDSource extends DnDDropActionHandler {

  boolean canStartDragging(DnDAction action, Point dragOrigin);

  DnDDragStartBean startDragging(DnDAction action, Point dragOrigin);

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
  @Deprecated
  @Nullable
  default Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
    return null;
  }

  void dragDropEnd();
}
