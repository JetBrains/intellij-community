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
package com.intellij.uiDesigner.core;

import java.awt.*;
import java.util.ArrayList;

public final class Util {
  private static final Dimension MAX_SIZE = new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
  public static final int DEFAULT_INDENT = 10;

  public static Dimension getMinimumSize(final Component component, final GridConstraints constraints, final boolean addIndent){
    try {
      final Dimension size = getSize(constraints.myMinimumSize, component.getMinimumSize());
      if (addIndent) {
        size.width += DEFAULT_INDENT * constraints.getIndent();
      }
      return size;
    } catch (NullPointerException npe) { //IDEA-80722
      return new Dimension(0,0);
    }
  }

  public static Dimension getMaximumSize(final Component component, final GridConstraints constraints, final boolean addIndent){
    try {
      //[anton] we use only our property for maximum size.
      // JButton reports that its max size = pref size, so it is impossible to make a column of same sized buttons.
      // Probably there are other bad cases...
      final Dimension size = getSize(constraints.myMaximumSize, MAX_SIZE);
      if (addIndent && size.width < MAX_SIZE.width) {
        size.width += DEFAULT_INDENT * constraints.getIndent();
      }
      return size;
    }
    catch (NullPointerException e) {//IDEA-80722
      return new Dimension(0,0);
    }
  }

  public static Dimension getPreferredSize(final Component component, final GridConstraints constraints, final boolean addIndent) {
    try {
      final Dimension size = getSize(constraints.myPreferredSize, component.getPreferredSize());
      if (addIndent) {
        size.width += DEFAULT_INDENT * constraints.getIndent();
      }
      return size;
    }
    catch (NullPointerException e) {//IDEA-80722
      return new Dimension(0,0);
    }
  }

  private static Dimension getSize(final Dimension overridenSize, final Dimension ownSize){
    final int overridenWidth = overridenSize.width >= 0 ? overridenSize.width : ownSize.width;
    final int overridenHeight = overridenSize.height >= 0 ? overridenSize.height : ownSize.height;
    return new Dimension(overridenWidth, overridenHeight);
  }

  public static void adjustSize(final Component component, final GridConstraints constraints, final Dimension size) {
    final Dimension minimumSize = getMinimumSize(component, constraints, false);
    final Dimension maximumSize = getMaximumSize(component, constraints, false);

    size.width = Math.max(size.width, minimumSize.width);
    size.height = Math.max(size.height, minimumSize.height);

    size.width = Math.min(size.width, maximumSize.width);
    size.height = Math.min(size.height, maximumSize.height);
  }

  /**
   * 
   * @param elimitated output parameter; will be filled indices (Integers) of eliminated cells. May be null. 
   * @return
   */ 
  public static int eliminate(final int[] cellIndices, final int[] spans, final ArrayList elimitated) {
    final int size = cellIndices.length;
    if (size != spans.length){
      throw new IllegalArgumentException("size mismatch: " + size + ", " + spans.length);
    }
    if (elimitated != null && elimitated.size() != 0) {
      throw new IllegalArgumentException("eliminated must be empty");
    }
    
    int cellCount = 0;
    for (int i = 0; i < size; i++) {
      cellCount = Math.max(cellCount, cellIndices[i] + spans[i]);
    }

    outer: for (int cell=cellCount - 1; cell >= 0; cell--) {
      // check if we should eliminate cell

      boolean starts = false;
      boolean ends = false;

      for (int i = 0; i < size; i++) {
        if (cellIndices[i] == cell) {
          starts = true;
        }
        if (cellIndices[i] + spans[i] - 1 == cell) {
          ends = true;
        }
      }

      if (starts && ends) {
        continue;
      }

      if (elimitated != null){
        elimitated.add(new Integer(cell));
      }
      
      // eliminate cell
      for (int i = 0; i < size; i++) {
        final boolean decreaseSpan = cellIndices[i] <= cell && cell < cellIndices[i] + spans[i];
        final boolean decreaseIndex = cellIndices[i] > cell;

        if (decreaseSpan) {
          spans[i]--;
        }

        if (decreaseIndex) {
          cellIndices[i]--;
        }
      }

      cellCount--;
    }

    return cellCount;
  }
}
