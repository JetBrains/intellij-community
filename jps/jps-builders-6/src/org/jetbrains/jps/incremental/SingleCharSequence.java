// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

/**
 * @author Eugene Zhuravlev
 * Date: 02-Oct-18
 */
public final class SingleCharSequence implements CharSequence {
  private final char myCh;

  public SingleCharSequence(char ch) {
    myCh = ch;
  }

  @Override
  public int length() {
    return 1;
  }

  @Override
  public char charAt(int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException("Index out of bounds: " + index);
    }
    return myCh;
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    throw new RuntimeException("Method subSequence not implemented");
  }

  public String toString() {
    return String.valueOf(myCh);
  }
}
