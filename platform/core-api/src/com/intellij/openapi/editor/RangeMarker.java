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

import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Represents a range of text in a {@link Document} which is automatically adjusted
 * as the document text is modified. Adding or deleting text before the marker
 * shifts the marker forward or backward; adding or deleting text within the marker
 * increases or decreases the length of the marked range by the respective offset. Adding
 * text at the start or end of the marker optionally extends the marker, depending on
 * {@link #setGreedyToLeft(boolean)} and {@link #setGreedyToRight(boolean)} settings. Deleting
 * the entire text range containing the marker causes the marker to become invalid.
 * <p>
 * <b>A note about lifetime.</b>
 * Range markers are weakly referenced and eventually got garbage-collected
 * after a long and painful fight with the garbage collector.
 * So calling its {@link #dispose()} method is not strictly necessary
 * (exactly like helping an old lady to cross the road is not strictly compulsory)
 * but it wouldn't hurt, and would actually help GC when <strike>the old lady is struggling</strike>
 * the allocation rate is very high.
 *
 * @see Document#createRangeMarker(int, int)
 */
public interface RangeMarker extends UserDataHolder, Segment {
  /**
   * Returns the document to which the marker belongs.
   *
   * @return the document instance.
   */
  @NotNull
  Document getDocument();

  /**
   * Returns the start offset of the text range covered by the marker.
   *
   * @return the start offset.
   */
  @Override
  @Contract(pure = true)
  int getStartOffset();

  /**
   * Returns the end offset of the text range covered by the marker.
   *
   * @return the end offset.
   */
  @Override
  @Contract(pure = true)
  int getEndOffset();

  /**
   * Checks whether the marker is still alive, or it has been invalidated, either by deleting
   * the entire fragment of text containing the marker, or by an explicit call to {@link #dispose()}.
   *
   * @return true if the marker is valid, false if it has been invalidated.
   */
  @Contract(pure = true)
  boolean isValid();

  /**
   * Sets the value indicating whether the text added exactly at the beginning of the
   * marker should be included in the text range of the marker. The default value is false.
   *
   * @param greedy true if text added at the beginning is included in the range, false otherwise.
   */
  void setGreedyToLeft(boolean greedy);

  /**
   * Sets the value indicating whether the text added exactly at the end of the
   * marker should be included in the text range of the marker. The default value is false.
   *
   * @param greedy true if text added at the end is included in the range, false otherwise.
   */
  void setGreedyToRight(boolean greedy);

  Comparator<? super RangeMarker> BY_START_OFFSET = BY_START_OFFSET_THEN_END_OFFSET;

  /**
   * @return true if the range marker should increase its length when a character is inserted at the {@link #getEndOffset()} offset.
   */
  boolean isGreedyToRight();

  /**
   * @return true if the range marker should increase its length when a character is inserted at the {@link #getStartOffset()} offset.
   */
  boolean isGreedyToLeft();

  /**
   * Destroys and de-registers the range marker.
   * <p>
   * From the moment this method is called, {@link #isValid()} starts returning {@code false},
   * and the behaviour of all other methods becomes undefined, which means they can throw exceptions.
   * Calling this method is not strictly necessary because range markers are garbage-collectable,
   * but may help improve performance in case of a high GC pressure (see the {@link RangeMarker} javadoc).
   */
  void dispose();

  /**
   * @return a {@link TextRange} with offsets of this range marker.
   * This method is preferable because the most implementations are thread-safe, so the returned range is always consistent, whereas
   * the more conventional {@code TextRange.create(getStartOffset(), getEndOffset())} could return inconsistent range when the selection
   * changed between {@link #getStartOffset()} and {@link #getEndOffset()} calls.
   */
  @NotNull
  default TextRange getTextRange() {
    return new TextRange(getStartOffset(), getEndOffset());
  }
}
