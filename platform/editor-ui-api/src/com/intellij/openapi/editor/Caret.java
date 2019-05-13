/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.UserDataHolderEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a specific caret instance in the editor.
 * Provides methods to query and modify caret position and caret's associated selection.
 * <p>
 * Instances of this interface are supposed to be obtained from {@link CaretModel} instance, and not created explicitly.
 */
public interface Caret extends UserDataHolderEx, Disposable {
  /**
   * Returns an instance of Editor, current caret belongs to.
   */
  @NotNull
  Editor getEditor();

  /**
   * Returns an instance of CaretModel, current caret is associated with.
   */
  @NotNull
  CaretModel getCaretModel();

  /**
   * Tells whether this caret is valid, i.e. recognized by the caret model currently. Caret is valid since its creation till its
   * removal from caret model.
   *
   * @see CaretModel#addCaret(VisualPosition)
   * @see CaretModel#removeCaret(Caret)
   */
  boolean isValid();

  /**
   * Moves the caret by the specified number of lines and/or columns.
   *
   * @param columnShift    the number of columns to move the caret by.
   * @param lineShift      the number of lines to move the caret by.
   * @param withSelection  if true, the caret move should extend the selection in the document.
   * @param scrollToCaret  if true, the document should be scrolled so that the caret is visible after the move.
   */
  void moveCaretRelatively(int columnShift,
                           int lineShift,
                           boolean withSelection,
                           boolean scrollToCaret);

  /**
   * Moves the caret to the specified logical position.
   * If corresponding position is in the folded region currently, the region will be expanded.
   *
   * @param pos the position to move to.
   */
  void moveToLogicalPosition(@NotNull LogicalPosition pos);

  /**
   * Moves the caret to the specified visual position.
   *
   * @param pos the position to move to.
   */
  void moveToVisualPosition(@NotNull VisualPosition pos);

  /**
   * Short hand for calling {@link #moveToOffset(int, boolean)} with {@code 'false'} as a second argument.
   *
   * @param offset      the offset to move to
   */
  void moveToOffset(int offset);

  /**
   * Moves the caret to the specified offset in the document.
   * If corresponding position is in the folded region currently, the region will be expanded.
   *
   * @param offset                  the offset to move to.
   * @param locateBeforeSoftWrap    there is a possible case that there is a soft wrap at the given offset, hence, the same offset
   *                                corresponds to two different visual positions - just before soft wrap and just after soft wrap.
   *                                We may want to clearly indicate where to put the caret then. Given parameter allows to do that.
   *                                <b>Note:</b> it's ignored if there is no soft wrap at the given offset
   */
  void moveToOffset(int offset, boolean locateBeforeSoftWrap);

  /**
   * Caret position may be updated on document change (e.g. consider that user updates from VCS that causes addition of text
   * before caret. Caret offset, visual and logical positions should be updated then). So, there is a possible case
   * that caret model in in the process of caret position update now.
   * <p/>
   * Current method allows to check that.
   *
   * @return    {@code true} if caret position is up-to-date for now; {@code false} otherwise
   */
  boolean isUpToDate();

  /**
   * Returns the logical position of the caret.
   *
   * @return the caret position.
   */
  @NotNull
  LogicalPosition getLogicalPosition();

  /**
   * Returns the visual position of the caret.
   *
   * @return the caret position.
   */
  @NotNull
  VisualPosition getVisualPosition();

  /**
   * Returns the offset of the caret in the document. Returns 0 for a disposed (invalid) caret.
   *
   * @return the caret offset.
   *
   * @see #isValid()
   */
  int getOffset();

  /**
   * @return    document offset for the start of the logical line where caret is located
   */
  int getVisualLineStart();

  /**
   * @return    document offset that points to the first symbol shown at the next visual line after the one with caret on it
   */
  int getVisualLineEnd();

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
  @NotNull
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
  @NotNull
  VisualPosition getSelectionEndPosition();

  /**
   * Returns the text selected in the editor.
   *
   * @return the selected text, or null if there is currently no selection.
   */
  @Nullable
  String getSelectedText();

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
  @NotNull
  VisualPosition getLeadSelectionPosition();

  /**
   * Checks if a range of text is currently selected.
   *
   * @return true if a range of text is selected, false otherwise.
   */
  boolean hasSelection();

  /**
   * Selects the specified range of text.
   * <p>
   * System selection will be updated, if such feature is supported by current editor.
   *
   * @param startOffset the start offset of the text range to select.
   * @param endOffset   the end offset of the text range to select.
   */
  void setSelection(int startOffset, int endOffset);

  /**
   * Selects the specified range of text.
   *
   * @param startOffset the start offset of the text range to select.
   * @param endOffset   the end offset of the text range to select.
   * @param updateSystemSelection whether system selection should be updated (might not have any effect if current editor doesn't support such a feature)
   */
  void setSelection(int startOffset, int endOffset, boolean updateSystemSelection);

  /**
   * Selects target range providing information about visual boundary of selection end.
   * <p/>
   * That is the case for soft wraps-aware processing where the whole soft wraps virtual space is matched to the same offset.
   * <p/>
   * Also, in column mode this method allows to create selection spanning virtual space after the line end.
   * <p>
   * System selection will be updated, if such feature is supported by current editor.
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
   * <p/>
   * Also, in column mode this method allows to create selection spanning virtual space after the line end.
   * <p>
   * System selection will be updated, if such feature is supported by current editor.
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
   * Selects target range based on its visual boundaries.
   * <p/>
   * That is the case for soft wraps-aware processing where the whole soft wraps virtual space is matched to the same offset.
   * <p/>
   * Also, in column mode this method allows to create selection spanning virtual space after the line end.
   *
   * @param startPosition   start visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param endPosition     end visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param startOffset     start selection offset
   * @param endOffset       end selection offset
   * @param updateSystemSelection whether system selection should be updated (might not have any effect if current editor doesn't support such a feature)
   */
  void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset, boolean updateSystemSelection);

  /**
   * Removes the selection in the editor.
   */
  void removeSelection();

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
   * Clones the current caret and positions the new one right above or below the current one. If current caret has selection, corresponding
   * selection will be set for the new caret.
   *
   * @param above if {@code true}, new caret will be created at the previous line, if {@code false} - on the next line
   * @return newly created caret instance, or {@code null} if the caret cannot be created because it already exists at the new location
   * or caret model doesn't support multiple carets.
   */
  @Nullable
  Caret clone(boolean above);

  /**
   * Returns {@code true} if caret is located in RTL text fragment. In that case visual column number is inversely related
   * to offset and logical column number in the vicinity of caret.
   */
  boolean isAtRtlLocation();

  /**
   * Returns {@code true} if caret is located at a boundary between different runs of bidirectional text.
   * This means that text fragments at different sides of the boundary are non-adjacent in logical order.
   * Caret can located at any side of the boundary, 
   * exact location can be determined from directionality flags of caret's logical and visual position 
   * ({@link LogicalPosition#leansForward} and {@link VisualPosition#leansRight}).
   */
  boolean isAtBidiRunBoundary();

  /**
   * Returns visual attributes currently set for the caret.
   *
   * @see #setVisualAttributes(CaretVisualAttributes)
   */
  @NotNull
  CaretVisualAttributes getVisualAttributes();

  /**
   * Sets caret's current visual attributes. This can have no effect if editor doesn't support changing caret's visual appearance.
   *
   * @see #getVisualAttributes()
   */
  void setVisualAttributes(@NotNull CaretVisualAttributes attributes);
}
