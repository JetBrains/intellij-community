/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceBackedByArray;
import org.jetbrains.annotations.NotNull;

/**
 * Provides generic contract for object encapsulating information about single unit of text change.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 *
 * @author Denis Zhdanov
 * @since May 31, 2010 12:26:51 PM
 */
public interface TextChange {

  /**
   * @return      start index (inclusive) of text range affected by the change encapsulated at the current object
   */
  int getStart();

  /**
   * @return      end index (exclusive) of text range affected by the change encapsulated at the current object
   */
  int getEnd();

  /**
   * Allows to retrieve text that is directly affected by the change encapsulated by the current object.
   *
   * @return    text related to the change encapsulated by the current object
   */
  @NotNull
  CharSequence getText();

  /**
   * Allows to get change text as a char array. Note that it's not guaranteed that change text directly maps to the returned char array,
   * i.e. change to array content is not obeyed to be reflected in {@link #getText()} result.
   * <p/>
   * Generally speaking, this method is introduced just as a step toward existing high-performance services that work in terms
   * of char arrays. Resulting array is instantiated on-demand via {@link CharArrayUtil#fromSequence(CharSequence)}, hence, it
   * doesn't hit memory if, for example, {@link CharSequenceBackedByArray} is used as initial change text.
   *
   * @return    stored change text as a char array
   */
  @NotNull
  char[] getChars();
}
