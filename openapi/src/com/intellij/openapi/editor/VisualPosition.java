/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;

/**
 * Represents a visual position in the editor. Visual positions take folding into account -
 * for example, if the top 10 lines of the document are folded, the 10th line in the document
 * will have the line number 1 in its visual position.
 *
 * @see LogicalPosition
 * @see Editor#logicalToVisualPosition(LogicalPosition)
 * @see Editor#offsetToVisualPosition(int)
 * @see Editor#xyToVisualPosition(java.awt.Point)  
 */
public class VisualPosition {
  public final int line;
  public final int column;

  public VisualPosition(int line, int column) {
    if (line < 0) throw new IllegalArgumentException("line must be non negative: "+line);
    if (column < 0) throw new IllegalArgumentException("column must be non negative: "+column);
    this.line = line;
    this.column = column;
  }

  @NonNls
  public String toString() {
    return "VisualPosition: line = " + line + " column = " + column;
  }
}
