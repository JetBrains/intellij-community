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

import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides generic contract for object encapsulating information about single unit of text change.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since May 31, 2010 12:26:51 PM
 */
public class TextChange {

  private final AtomicReference<char[]> myChars = new AtomicReference<char[]>();
  private final CharSequence myText;
  private final int myStart;
  private final int myEnd;

  /**
   * Shorthand for creating {@link TextChange} with the given arguments where <code>'end index'</code> has the same value as
   * <code>'start index'</code>.
   *
   * @param text      text affected by the current change
   * @param start     start index (inclusive) of text range affected by the change encapsulated by the current object
   * @throws IllegalArgumentException     if given start index is invalid
   */
  public TextChange(@NotNull CharSequence text, int start) throws IllegalArgumentException {
    this(text, start, start);
  }

  /**
   * Creates new <code>TextChange</code> object with the given arguments. It encapsulates information about the change that
   * may be applied to the target document.
   *
   * @param text      text that is introduced by the current change
   * @param start     start index of the target document location where current change is to be applied
   * @param end       end index of the target document where current change is to be applied, i.e. it's assumed that current text
   *                  change appliance to particular document causes replacement of its original text at <code>[start; end)</code>
   *                  interval by the text encapsulated by the current change. I.e. original text is replaced by the new one
   * @throws IllegalArgumentException     if given start or end index in invalid or they are inconsistent to each other
   */
  public TextChange(@NotNull CharSequence text, int start, int end) throws IllegalArgumentException {
    if (start < 0) {
      throw new IllegalArgumentException(String.format("Can't construct new %s object. Reason: given start index (%d) is negative. "
                                                       + "End index: %d, text: '%s'", getClass().getName(), start, end, text));
    }
    if (end < start) {
      throw new IllegalArgumentException(String.format("Can't construct new %s object. Reason: given end index (%d) is less than "
                                                       + "start index (%d). Text: '%s'", getClass().getName(), end, start, text));
    }
    myText = text;
    myStart = start;
    myEnd = end;
  }

  /**
   * @return      start index (inclusive) of text range affected by the change encapsulated at the current object
   */
  public int getStart() {
    return myStart;
  }

  /**
   * @return      end index (exclusive) of text range affected by the change encapsulated at the current object
   */
  public int getEnd() {
    return myEnd;
  }


  /**
   * Allows to retrieve text that is directly affected by the change encapsulated by the current object.
   *
   * @return    text related to the change encapsulated by the current object
   */
  @NotNull
  public CharSequence getText() {
    return myText;
  }

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
  public char[] getChars() {
    char[] result = myChars.get();
    if (result != null) {
      return result;
    }
    myChars.compareAndSet(null, CharArrayUtil.fromSequence(myText));
    return myChars.get();
  }

  /**
   * Difference in document symbols number after current change appliance.
   * <p/>
   * <b>Note:</b> returned number may be either positive or not. For example it may be negative for <code>'remove'</code>
   * or <code>'replace'</code> changes (number of text symbols is less than number of symbols at target change interval)
   *
   * @return    difference in document symbols number after current change appliance
   */
  public int getDiff() {
    return myText.length() - myEnd + myStart;
  }

  /**
   * Creates new {@link TextChange} on the basis of the current object with given offset applied to its {@link #getStart() start}
   * and {@link #getEnd() end} properties.
   *
   * @param offset    offset to apply to the current change object
   * @return          text change that is built on the basis of the current object that with {@link #getStart() start}
   *                  and {@link #getEnd() end} positions shifted to the given offset
   * @throws IllegalArgumentException   if start index becomes zero after given offset appliance (it is not applied then)
   */
  public TextChange advance(int offset) throws IllegalArgumentException {
    if (offset == 0) {
      return this;
    }
    int newStart = myStart + offset;
    if (newStart < 0) {
      throw new IllegalArgumentException(String.format("Can't apply given offset (%d) to the current text change object (%s). Reason: "
                                                       + "new start index becomes negative after that (%d)", offset, this, newStart));
    }

    return new TextChange(myText, newStart, myEnd + offset);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TextChange that = (TextChange)o;
    return myText.equals(that.myText) && myStart == that.myStart && myEnd == that.myEnd;
  }

  @Override
  public int hashCode() {
    int result = myText.hashCode();
    result = 31 * result + myStart;
    return 31 * result + myEnd;
  }

  @Override
  public String toString() {
    return String.format("%d-%d: '%s'", myStart, myEnd, myText);
  }
}
