// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

final class SingleCharSequence implements CharSequence {
  private final char myCh;

  SingleCharSequence(char ch) {
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

  @Override
  public String toString() {
    return String.valueOf(myCh);
  }
}
