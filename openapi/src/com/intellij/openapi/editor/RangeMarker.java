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

import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a range of text in a {@link Document} which is automatically adjusted
 * as the document text is modified. Adding or deleting text before the marker
 * shifts the marker forward or backward; adding or deleting text within the marker
 * increases or decreases the length of the marked range by the respective offset. Adding
 * text at the start or end of the marker optionally extends the marker, depending on
 * {@link #setGreedyToLeft(boolean)} and {@link #setGreedyToRight(boolean)} settings. Deleting
 * the entire text range containing the marker causes the marker to become invalid.
 *
 * @see Document#createRangeMarker(int, int)
 */
public interface RangeMarker extends UserDataHolder{
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
  int getStartOffset();

  /**
   * Returns the end offset of the text range covered by the marker.
   *
   * @return the end offset.
   */
  int getEndOffset();

  /**
   * Checks if the marker has been invalidated by deleting the entire fragment of text
   * containing the marker.
   *
   * @return true if the marker is valid, false if it has been invalidated.
   */
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
}