/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides services for selecting text in the IDEA text editor and retrieving information about the selection. 
 * Most of the methods here exist for compatibility reasons, corresponding functionality is also provided by {@link CaretModel} now.
 * <p>
 * In editor supporting multiple carets, each caret has its own associated selection range. Unless mentioned explicitly, methods of this 
 * interface operate on the current caret (see {@link CaretModel#runForEachCaret(CaretAction)}), or 'primary' caret if current caret 
 * is not defined. 
 *
 * @see Editor#getSelectionModel()
 * @see CaretModel
 */
public interface SelectionModel {
  /**
   * Returns the start offset in the document of the selected text range, or the caret
   * position if there is currently no selection.
   *
   * @return the selection start offset.
   */
  int getSelectionStart();

  /**
   * @return    object that encapsulates information about visual position of selected text start if any
   */
  @Nullable
  VisualPosition getSelectionStartPosition();

  /**
   * Returns the end offset in the document of the selected text range, or the caret
   * position if there is currently no selection.
   *
   * @return the selection end offset.
   */
  int getSelectionEnd();

  /**
   * @return    object that encapsulates information about visual position of selected text end if any;
   */
  @Nullable
  VisualPosition getSelectionEndPosition();

  /**
   * Returns the text selected in the editor.
   *
   * @return the selected text, or null if there is currently no selection.
   */
  @Nullable
  String getSelectedText();

  /**
   * If {@code allCarets} is {@code true}, returns the concatenation of selections for all carets, or {@code null} if there
   * are no selections. If {@code allCarets} is {@code false}, works just like {@link #getSelectedText}.
   */
  @Nullable
  String getSelectedText(boolean allCarets);

  /**
   * Returns the offset from which the user started to extend the selection (the selection start
   * if the selection was extended in forward direction, or the selection end if it was
   * extended backward).
   *
   * @return the offset from which the selection was started, or the caret offset if there is
   *         currently no selection.
   */
  int getLeadSelectionOffset();

  /**
   * @return    object that encapsulates information about visual position from which the user started to extend the selection if any
   */
  @Nullable
  VisualPosition getLeadSelectionPosition();

  /**
   * Checks if a range of text is currently selected.
   *
   * @return true if a range of text is selected, false otherwise.
   */
  boolean hasSelection();

  /**
   * Checks if a range of text is currently selected. If {@code anyCaret} is {@code true}, check all existing carets in
   * the document, and returns {@code true} if any of them has selection, otherwise checks only the current caret.
   *
   * @return true if a range of text is selected, false otherwise.
   */
  boolean hasSelection(boolean anyCaret);

  /**
   * Selects the specified range of text.
   *
   * @param startOffset the start offset of the text range to select.
   * @param endOffset   the end offset of the text range to select.
   */
  void setSelection(int startOffset, int endOffset);

  /**
   * Selects target range providing information about visual boundary of selection end.
   * <p/>
   * That is the case for soft wraps-aware processing where the whole soft wraps virtual space is matched to the same offset.
   *
   * @param startOffset     start selection offset
   * @param endPosition     end visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param endOffset       end selection offset
   */
  void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset);

  /**
   * Selects target range based on its visual boundaries.
   * <p/>
   * That is the case for soft wraps-aware processing where the whole soft wraps virtual space is matched to the same offset.
   *
   * @param startPosition   start visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param endPosition     end visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param startOffset     start selection offset
   * @param endOffset       end selection offset
   */
  void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset);

  /**
   * Removes the selection in the editor.
   */
  void removeSelection();

  /**
   * Removes the selection in the editor. If {@code allCarets} is {@code true}, removes selections from all carets in the
   * editor, otherwise, does this just for the current caret.
   */
  void removeSelection(boolean allCarets);

  /**
   * Adds a listener for receiving information about selection changes.
   *
   * @param listener the listener instance.
   */
  void addSelectionListener(SelectionListener listener);

  /**
   * Removes a listener for receiving information about selection changes.
   *
   * @param listener the listener instance.
   */
  void removeSelectionListener(SelectionListener listener);

  /**
   * Selects the entire line of text at the caret position.
   */
  void selectLineAtCaret();

  /**
   * Selects the entire word at the caret position, optionally using camel-case rules to
   * determine word boundaries.
   *
   * @param honorCamelWordsSettings if true and "Use CamelHumps words" is enabled,
   *                                upper-case letters within the word are considered as
   *                                boundaries for the range of text to select.
   */
  void selectWordAtCaret(boolean honorCamelWordsSettings);

  /**
   * Copies the currently selected text to the clipboard.
   *
   * When multiple selections exist in the document, all of them are copied, as a single piece of text.
   */
  void copySelectionToClipboard();

  /**
   * Creates a multi-caret selection for the rectangular block of text with specified start and end positions.
   *
   * @param blockStart the start of the rectangle to select.
   * @param blockEnd   the end of the rectangle to select.
   * @see #setSelection(int, int)
   */
  void setBlockSelection(@NotNull LogicalPosition blockStart, @NotNull LogicalPosition blockEnd);

  /**
   * Returns an array of start offsets in the document for ranges selected in the document currently. Works both for a single-caret and
   * a multiple-caret selection (for carets not having a selection, caret position is returned).
   *
   * @return an array of start offsets, array size is equal to the number of carets existing in the editor currently.
   */
  @NotNull
  int[] getBlockSelectionStarts();

  /**
   * Returns an array of end offsets in the document for ranges selected in the document currently. Works both for a single-caret and
   * a multiple-caret selection (for carets not having a selection, caret position is returned).
   *
   * @return an array of start offsets, array size is equal to the number of carets existing in the editor currently.
   */
  @NotNull
  int[] getBlockSelectionEnds();

  /**
   * Returns visual representation of selection.
   *
   * @return Selection attributes.
   */
  TextAttributes getTextAttributes();
}
