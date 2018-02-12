/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Provides services for moving the caret and retrieving information about caret position.
 *
 * May support several carets existing simultaneously in a document. {@link #supportsMultipleCarets()} method can be used to find out
 * whether particular instance of CaretModel does it. If it does, query and update methods for caret position operate on a certain 'primary'
 * caret. There exists a way to perform the same operation(s) on each caret - see
 * {@link #runForEachCaret(CaretAction)} method. Within its context, query and update methods operate on the
 * current caret in that iteration. This behaviour can change in future though, so using caret and selection query and update methods in
 * actions that need to operate on multiple carets is discouraged - methods on {@link Caret} instances obtained
 * via {@link #getAllCarets()} or {@link #runForEachCaret(CaretAction)} should be used instead.
 * <p>
 * How 'primary' caret is determined by the model is not defined (currently it's the most recently added caret, but that can change).
 * <p>
 * At all times at least one caret will exist in a document.
 * <p>
 * Update methods, {@link #runBatchCaretOperation(Runnable)} and {@link #runForEachCaret(CaretAction)} methods
 * should only be run from EDT. Query methods can be run from any thread, when called not from EDT, those methods are 'not aware' of
 * 'runForEachCaret' scope - they will always return information about primary caret.
 *
 * @see Editor#getCaretModel()
 */
public interface CaretModel {
  /**
   * Moves the caret by the specified number of lines and/or columns.
   *
   * @param columnShift    the number of columns to move the caret by.
   * @param lineShift      the number of lines to move the caret by.
   * @param withSelection  if true, the caret move should extend the selection range in the document.
   * @param blockSelection This parameter is currently ignored.
   * @param scrollToCaret  if true, the document should be scrolled so that the caret is visible after the move.
   */
  void moveCaretRelatively(int columnShift,
                           int lineShift,
                           boolean withSelection,
                           boolean blockSelection,
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
   * Returns the offset of the caret in the document.
   *
   * @return the caret offset.
   */
  int getOffset();

  /**
   * Adds a listener for receiving notifications about caret movement and caret addition/removal
   *
   * @param listener the listener instance.
   */
  void addCaretListener(@NotNull CaretListener listener);

  /**
   * Removes a listener for receiving notifications about caret movement and caret addition/removal
   *
   * @param listener the listener instance.
   */
  void removeCaretListener(@NotNull CaretListener listener);

  /**
   * @return    document offset for the start of the logical line where caret is located
   */
  int getVisualLineStart();

  /**
   * @return    document offset that points to the first symbol shown at the next visual line after the one with caret on it
   */
  int getVisualLineEnd();

  /**
   * Returns visual representation of caret (e.g. background color).
   *
   * @return Caret attributes.
   */
  TextAttributes getTextAttributes();

  /**
   * Tells whether multiple coexisting carets are supported by this CaretModel instance. Multiple carets
   * are not supported when the corresponding Editor instance is a facade over a Swing JTextArea component.
   */
  boolean supportsMultipleCarets();

  /**
   * Returns current caret - the one, query and update methods in the model operate at the moment. In the current implementation this is
   * either an iteration-current caret within the context of {@link #runForEachCaret(CaretAction)} method, or the 'primary' caret without that
   * context. Users {@link #runForEachCaret(CaretAction)} method should use caret parameter passed to
   * {@link CaretAction#perform(Caret)} method instead of this method, as the definition of current caret (as
   * well as caret instance operated on by model methods) can potentially change.
   */
  @NotNull
  Caret getCurrentCaret();

  /**
   * Returns the 'primary' caret.
   */
  @NotNull
  Caret getPrimaryCaret();

  /**
   * Returns number of carets currently existing in the document
   */
  int getCaretCount();

  /**
   * Returns all carets currently existing in the document, ordered by their position in the document.
   */
  @NotNull
  List<Caret> getAllCarets();

  /**
   * Returns a caret at the given position in the document, or {@code null}, if there's no caret there.
   */
  @Nullable
  Caret getCaretAt(@NotNull VisualPosition pos);

  /**
   * Same as {@link #addCaret(VisualPosition, boolean)} with {@code true} as a {@code makePrimary} boolean parameter value.
   */
  @Nullable
  Caret addCaret(@NotNull VisualPosition pos);

  /**
   * Adds a new caret at the given position, and returns corresponding {@link Caret} instance. Locations outside of possible values
   * for the given document are trimmed automatically.
   * Newly added caret will become a primary caret if and only if {@code makePrimary} value is {@code true}.
   * Does nothing if multiple carets are not supported, a caret already exists at specified location or selection of existing caret
   * includes the specified location, {@code null} is returned in this case.
   */
  @Nullable
  Caret addCaret(@NotNull VisualPosition pos, boolean makePrimary);

  /**
   * Removes a given caret if it's recognized by the model and is not the only existing caret in the document, returning {@code true}.
   * {@code false} is returned if any of the above condition doesn't hold, and the removal cannot happen.
   */
  boolean removeCaret(@NotNull Caret caret);

  /**
   * Removes all carets except the 'primary' one from the document.
   */
  void removeSecondaryCarets();

  /**
   * Sets the number of carets, their positions and selection ranges according to the provided data. Null values for caret position or
   * selection boundaries will mean that corresponding caret's position and/or selection won't be changed.
   * <p>
   * System selection will be updated, if such feature is supported by current editor.
   *
   * @throws IllegalArgumentException if {@code caretStates} list is empty, or if it contains more than one element and editor doesn't
   * support multiple carets
   *
   * @see #supportsMultipleCarets()
   * @see #getCaretsAndSelections()
   * @see #setCaretsAndSelections(List, boolean)
   */
  void setCaretsAndSelections(@NotNull List<CaretState> caretStates);

  /**
   * Sets the number of carets, their positions and selection ranges according to the provided data. Null values for caret position or
   * selection boundaries will mean that corresponding caret's position and/or selection won't be changed.
   * <p>
   * System selection will be updated, if such feature is supported by current editor
   * and corresponding invocation parameter is set to {@code true}.
   *
   * @throws IllegalArgumentException if {@code caretStates} list is empty, or if it contains more than one element and editor doesn't
   * support multiple carets
   *
   * @see #supportsMultipleCarets()
   * @see #getCaretsAndSelections()
   */
  void setCaretsAndSelections(@NotNull List<CaretState> caretStates, boolean updateSystemSelection);

  /**
   * Returns the current positions of all carets and their selections. The order of entries in the returned list does not necessarily
   * correspond to the order of {@link #getAllCarets()} method results. Passing the result of this method to
   * {@link #setCaretsAndSelections(List)} will restore the state of carets, including the internal caret order, in particular,
   * the caret, that was primary when this method was called, will be the primary one after corresponding
   * {@link #setCaretsAndSelections(List)} invocation.
   *
   * @see #setCaretsAndSelections(List)
   */
  @NotNull
  List<CaretState> getCaretsAndSelections();

  /**
   * Same as {@link #runForEachCaret(CaretAction, boolean)} with {@code reverseOrder} set to {@code false}
   */
  void runForEachCaret(@NotNull CaretAction action);

  /**
   * Executes the given task for each existing caret. Set of carets to iterate over is
   * determined in the beginning and is not affected by the potential carets addition or removal by the task being executed.
   * At the end, merging of carets and selections is performed, so that no two carets will occur at the same logical position and
   * no two selection will overlap after this method is finished.
   * <p>
   * Carets are iterated in position order (top-to-bottom) if {@code reverseOrder} is {@code false}, and in reverse order
   * if it's {@code true}.
   */
  void runForEachCaret(@NotNull CaretAction action, boolean reverseOrder);

  /**
   * Executes the given task, performing caret merging afterwards. Caret merging will not happen until the operation is finished.
   */
  void runBatchCaretOperation(@NotNull Runnable runnable);
}
