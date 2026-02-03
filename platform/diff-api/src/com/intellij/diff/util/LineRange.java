// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.openapi.diagnostic.Logger;

public final class LineRange {
  private static final Logger LOG = Logger.getInstance(LineRange.class);

  public final int start;
  public final int end;

  public LineRange(int start, int end) {
    this.start = start;
    this.end = end;

    if (start > end) {
      LOG.error("LineRange is invalid: " + toString());
    }
  }

  public boolean contains(int start, int end) {
    return this.start <= start && this.end >= end;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LineRange range = (LineRange)o;

    if (start != range.start) return false;
    if (end != range.end) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = start;
    result = 31 * result + end;
    return result;
  }

  @Override
  public String toString() {
    return "[" + start + ", " + end + ")";
  }

  public boolean isEmpty() {
    return start == end;
  }
}
