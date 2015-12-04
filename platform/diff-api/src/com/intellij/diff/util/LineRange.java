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
package com.intellij.diff.util;

import com.intellij.openapi.diagnostic.Logger;

public class LineRange {
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
