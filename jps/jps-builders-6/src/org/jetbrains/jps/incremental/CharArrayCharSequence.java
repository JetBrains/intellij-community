// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 * Date: 02-Oct-18
 */
public class CharArrayCharSequence implements CharSequence {
  protected final char[] myChars;
  protected final int myStart;
  protected final int myEnd;

  public CharArrayCharSequence(@NotNull char[] chars) {
    this(chars, 0, chars.length);
  }

  public CharArrayCharSequence(@NotNull char[] chars, int start, int end) {
    if (start < 0 || end > chars.length || start > end) {
      throw new IndexOutOfBoundsException("chars.length:" + chars.length + ", start:" + start + ", end:" + end);
    }
    myChars = chars;
    myStart = start;
    myEnd = end;
  }

  public char[] getBackendArray() {
    return myChars;
  }

  public int getStart() {
    return myStart;
  }

  public int getEnd() {
    return myEnd;
  }

  @Override
  public final int length() {
    return myEnd - myStart;
  }

  @Override
  public final char charAt(int index) {
    return myChars[index + myStart];
  }

  @NotNull
  @Override
  public CharSequence subSequence(int start, int end) {
    return start == 0 && end == length() ? this : new CharArrayCharSequence(myChars, myStart + start, myStart + end);
  }

  @Override
  @NotNull
  public String toString() {
    return new String(myChars, myStart, myEnd - myStart);
  }
}
