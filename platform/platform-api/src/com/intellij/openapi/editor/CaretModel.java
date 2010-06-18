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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.TextAttributes;

/**
 * Provides services for moving the caret and retrieving information about caret position.
 *
 * @see Editor#getCaretModel()
 */
public interface CaretModel {
  /**
   * Moves the caret by the specified number of lines and/or columns.
   *
   * @param columnShift    the number of columns to move the caret by.
   * @param lineShift      the number of lines to move the caret by.
   * @param withSelection  if true, the caret move should extend the range or block selection in the document.
   * @param blockSelection if true and <code>withSelection</code> is true, the caret move should extend
   *                       the block selection in the document.
   * @param scrollToCaret  if true, the document should be scrolled so that the caret is visible after the move.
   */
  void moveCaretRelatively(int columnShift,
                           int lineShift,
                           boolean withSelection,
                           boolean blockSelection,
                           boolean scrollToCaret);

  /**
   * Moves the caret to the specified logical position.
   *
   * @param pos the position to move to.
   */
  void moveToLogicalPosition(LogicalPosition pos);

  /**
   * Moves the caret to the specified visual position.
   *
   * @param pos the position to move to.
   */
  void moveToVisualPosition(VisualPosition pos);

  /**
   * Moves the caret to the specified offset in the document.
   *
   * @param offset the offset to move to.
   */
  void moveToOffset(int offset);

  /**
   * Returns the logical position of the caret.
   *
   * @return the caret position.
   */
  LogicalPosition getLogicalPosition();

  /**
   * Returns the visual position of the caret.
   *
   * @return the caret position.
   */
  VisualPosition getVisualPosition();

  /**
   * Returns the offset of the caret in the document.
   *
   * @return the caret offset.
   */
  int getOffset();

  /**
   * Adds a listener for receiving notifications about caret movement.
   *
   * @param listener the listener instance.
   */
  void addCaretListener(CaretListener listener);

  /**
   * Removes a listener for receiving notifications about caret movement.
   * 
   * @param listener the listener instance.
   */
  void removeCaretListener(CaretListener listener);

  /**
   * @return    document offset for the start of the logical line where caret is located
   */
  int getVisualLineStart();

  /**
   * @return    document offset for the end of the logical line where caret is located
   */
  int getVisualLineEnd();

  TextAttributes getTextAttributes();
}
