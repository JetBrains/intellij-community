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
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.Editor;

import java.awt.*;

/**
 * Interface which should be implemented in order to paint custom markers in the line
 * marker area (over the folding area).
 *
 * @see RangeHighlighter#setLineMarkerRenderer(LineMarkerRenderer)
 */
public interface LineMarkerRenderer {
  /**
   * Draws the line marker over the specified rectangle.
   *
   * @param editor the editor to which the line marker belongs.
   * @param g      the graphics in which the line marker should be painted.
   * @param r      the rectangle in which the line marker should be painted
   *               (the left and right coordinates are defined by the area in which
   *               line markers may be painted, and the top and bottom coordinates are
   *               the top of the first line covered by the associated range highlighter
   *               and the bottom of the last line).
   */
  void paint(Editor editor, Graphics g, Rectangle r);
}